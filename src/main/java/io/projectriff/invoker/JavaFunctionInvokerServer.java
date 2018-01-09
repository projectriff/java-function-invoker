/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.projectriff.invoker;

import java.util.function.Function;

import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.MessageFunctionGrpc;

import com.google.gson.Gson;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;

/**
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Dave Syer
 */
public class JavaFunctionInvokerServer
		extends MessageFunctionGrpc.MessageFunctionImplBase {

	private final Function<Flux<Message<?>>, Flux<Message<?>>> function;
	private final Class<?> inputType;
	private final Class<?> outputType;
	private final Gson mapper;

	public JavaFunctionInvokerServer(Function<Flux<?>, Flux<?>> function, Gson mapper,
			Class<?> inputType, Class<?> outputType, boolean isMessage) {
		this.mapper = mapper;
		this.inputType = inputType;
		this.outputType = outputType;
		this.function = isMessage
				? flux -> function.apply(flux.map(this::message)).map(this::message)
				: flux -> function.apply(flux.map(this::payload)).map(this::message);
	}

	private Message<?> message(Object input) {
		return input instanceof Message ? (Message<?>) input
				: MessageBuilder.withPayload(input).build();
	}

	private Object payload(Message<?> input) {
		return input.getPayload();
	}

	@Override
	public StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> call(
			StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> responseObserver) {

		UnicastProcessor<Message<?>> emitter = UnicastProcessor.<Message<?>>create();
		function.apply(emitter).subscribe(
				message -> responseObserver
						.onNext(MessageConversionUtils.toGrpc(payloadToBytes(message))),
				responseObserver::onError, responseObserver::onCompleted);

		return new StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message>() {

			@Override
			public void onNext(
					io.projectriff.grpc.function.FunctionProtos.Message message) {
				emitter.onNext(
						payloadFromBytes(MessageConversionUtils.fromGrpc(message)));
			}

			@Override
			public void onError(Throwable t) {
				emitter.onError(t);
			}

			@Override
			public void onCompleted() {
				emitter.onComplete();
			}
		};

	}

	private Message<byte[]> payloadToBytes(Message<?> message) {
		return MessageBuilder.createMessage(toBytes(message.getPayload()),
				message.getHeaders());
	}

	private byte[] toBytes(Object payload) {
		if (payload instanceof byte[]) {
			return (byte[]) payload;
		}
		if (CharSequence.class.isAssignableFrom(outputType)) {
			return payload.toString().getBytes();
		}
		try {
			return mapper.toJson(payload).getBytes();
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert from " + outputType, e);
		}
	}

	private Message<?> payloadFromBytes(Message<byte[]> message) {
		return MessageBuilder.createMessage(fromBytes(message.getPayload()),
				message.getHeaders());
	}

	private Object fromBytes(byte[] payload) {
		if (byte[].class.isAssignableFrom(inputType)) {
			return payload;
		}
		if (CharSequence.class.isAssignableFrom(inputType)) {
			return new String(payload);
		}
		try {
			return mapper.fromJson(new String(payload), inputType);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert to " + inputType, e);
		}
	}
}
