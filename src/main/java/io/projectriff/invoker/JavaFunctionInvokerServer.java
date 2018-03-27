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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.MessageFunctionGrpc;

import com.google.gson.Gson;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.core.publisher.UnicastProcessor;

/**
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Dave Syer
 */
public class JavaFunctionInvokerServer
		extends MessageFunctionGrpc.MessageFunctionImplBase {

	private final Function<Flux<Message<?>>, Flux<Message<?>>> function;
	private final MessageConversionUtils output;
	private final MessageConversionUtils input;

	public JavaFunctionInvokerServer(Function<Flux<?>, Flux<?>> function, Gson mapper,
			Class<?> inputType, Class<?> outputType, boolean isMessage) {
		this.output = new MessageConversionUtils(mapper, outputType);
		this.input = new MessageConversionUtils(mapper, inputType);
		this.function = preserveHeaders(flux -> function
				.apply(flux.map(MessageConversionUtils.input(isMessage, function)))
				.map(MessageConversionUtils.output(isMessage, function)));
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

	@Override
	public StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> call(
			StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> responseObserver) {

		UnicastProcessor<Message<?>> emitter = UnicastProcessor.<Message<?>>create();
		GuardedFlux flux = new GuardedFlux(emitter);
		Flux<Message<?>> result = function.apply(flux);
		flux.setSubscribed(true);
		result.subscribe(
				message -> responseObserver.onNext(
						MessageConversionUtils.toGrpc(output.payloadToBytes(message))),
				t -> responseObserver
						.onError(ExceptionConverter.createStatus(t).asException()),
				responseObserver::onCompleted);

		return new StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message>() {

			@Override
			public void onNext(
					io.projectriff.grpc.function.FunctionProtos.Message message) {
				emitter.onNext(
						input.payloadFromBytes(MessageConversionUtils.fromGrpc(message)));
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

	private final static class GuardedFlux extends Flux<Message<?>> {
		private boolean subscribed;
		private final Flux<Message<?>> emitter;

		private GuardedFlux(Flux<Message<?>> emitter) {
			this.emitter = emitter;
		}

		@Override
		public void subscribe(CoreSubscriber<? super Message<?>> actual) {
			if (subscribed) {
				emitter.subscribe(actual);
			}
			else {
				Operators.error(actual, new IllegalStateException(
						"Cannot subscribe inside user function"));
			}
		}

		public void setSubscribed(boolean subscribed) {
			this.subscribed = subscribed;
		}
	}

}
