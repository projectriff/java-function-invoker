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

import java.util.function.Consumer;

import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.MessageFunctionGrpc;

import com.google.gson.Gson;

import org.springframework.messaging.Message;

import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;

/**
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Dave Syer
 */
public class JavaConsumerInvokerServer
		extends MessageFunctionGrpc.MessageFunctionImplBase {

	private final Consumer<Flux<Message<?>>> function;
	private final MessageConversionUtils input;

	public JavaConsumerInvokerServer(Consumer<Flux<?>> function, Gson mapper,
			Class<?> inputType, boolean isMessage) {
		this.input = new MessageConversionUtils(mapper, inputType);
		this.function = flux -> function
				.accept(flux.map(MessageConversionUtils.input(isMessage, function)));
	}

	@Override
	public StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> call(
			StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> responseObserver) {

		EmitterProcessor<Message<?>> emitter = EmitterProcessor.<Message<?>>create();
		function.accept(emitter.doOnComplete(responseObserver::onCompleted)
				.doOnError(t -> responseObserver
						.onError(ExceptionConverter.createStatus(t).asException())));

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

}
