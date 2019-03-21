/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.projectriff.invoker;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.MessageFunctionGrpc;

import com.google.gson.Gson;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;

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

	private static final Log logger = LogFactory.getLog(JavaFunctionInvokerServer.class);

	public JavaFunctionInvokerServer(Function<Flux<?>, Flux<?>> function, Gson mapper,
			Class<?> inputType, Class<?> outputType, boolean isMessage) {
		this.mapper = mapper;
		this.inputType = inputType;
		this.outputType = outputType;
		this.function = preserveHeaders(
				flux -> function.apply(flux.map(input(isMessage, function)))
						.map(output(isMessage, function)));
	}

	private Function<Flux<Message<?>>, Flux<Message<?>>> preserveHeaders(
			Function<Flux<Message<?>>, Flux<Message<?>>> function) {
		AtomicReference<Map<String, Object>> headers = new AtomicReference<>();
		return messages -> function.apply(messages.map(m -> storeHeaders(headers, m)))
				.map(m -> retrieveHeaders(headers, m));
	}

	private Message<?> retrieveHeaders(AtomicReference<Map<String, Object>> headers,
			Message<?> message) {
		Map<String, Object> map = headers.getAndSet(Collections.emptyMap());
		if (map.isEmpty()) {
			return message;
		}
		return MessageBuilder.fromMessage(message).copyHeadersIfAbsent(map).build();
	}

	private Message<?> storeHeaders(AtomicReference<Map<String, Object>> headers,
			Message<?> value) {
		headers.set(value.getHeaders());
		return value;
	}

	private Function<Message<?>, Object> input(boolean isMessage,
			Function<?, ?> function) {
		if (!isMessage) {
			return message -> message.getPayload();
		}
		return message -> MessageUtils.create(function, message.getPayload(),
				message.getHeaders());
	}

	private Function<Object, Message<?>> output(boolean isMessage,
			Function<?, ?> function) {
		if (!isMessage) {
			return payload -> MessageBuilder.withPayload(payload).build();
		}
		return message -> MessageUtils.unpack(function, message);
	}

	@Override
	public StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> call(
			StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> responseObserver) {

		EmitterProcessor<Message<?>> emitter = EmitterProcessor.<Message<?>>create();
		function.apply(emitter).subscribe(
				message -> responseObserver
						.onNext(MessageConversionUtils.toGrpc(payloadToBytes(message))),
				t -> responseObserver
						.onError(ExceptionConverter.createStatus(t).asException()),
				responseObserver::onCompleted);

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
		if (logger.isDebugEnabled()) {
			logger.debug("Outgoing: " + message);
		}
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
		Message<Object> result = MessageBuilder
				.createMessage(fromBytes(message.getPayload()), message.getHeaders());
		if (logger.isDebugEnabled()) {
			logger.debug("Incoming: " + result);
		}
		return result;
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
