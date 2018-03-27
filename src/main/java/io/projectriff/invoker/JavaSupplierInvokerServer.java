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

import java.util.function.Supplier;

import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.FunctionProtos.Trigger;
import io.projectriff.grpc.function.MessageSourceGrpc;

import com.google.gson.Gson;

import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

/**
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Dave Syer
 */
public class JavaSupplierInvokerServer extends MessageSourceGrpc.MessageSourceImplBase {

	private final Supplier<Flux<Message<?>>> supplier;
	private final MessageConversionUtils converter;

	public JavaSupplierInvokerServer(Supplier<Flux<?>> supplier, Gson mapper,
			Class<?> outputType, boolean isMessage) {
		this.converter = new MessageConversionUtils(mapper, outputType);
		this.supplier = () -> supplier.get()
				.map(MessageConversionUtils.output(isMessage, supplier));
	}

	@Override
	public void call(Trigger request,
			StreamObserver<io.projectriff.grpc.function.FunctionProtos.Message> responseObserver) {

		supplier.get().subscribe(
				message -> responseObserver.onNext(
						MessageConversionUtils.toGrpc(converter.payloadToBytes(message))),
				t -> responseObserver
						.onError(ExceptionConverter.createStatus(t).asException()),
				responseObserver::onCompleted);

	}

}
