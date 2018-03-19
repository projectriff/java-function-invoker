/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.FunctionProtos.Message;
import io.projectriff.grpc.function.FunctionProtos.Trigger;
import io.projectriff.grpc.function.MessageSourceGrpc;
import io.projectriff.grpc.function.MessageSourceGrpc.MessageSourceStub;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Dave Syer
 *
 */
public class SourceTestClient {
	private final Log logger = LogFactory.getLog(SourceTestClient.class);

	private final ManagedChannel channel;
	private final MessageSourceStub asyncStub;

	public SourceTestClient(String host, int port) {
		this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true));
	}

	public SourceTestClient(ManagedChannelBuilder<?> channelBuilder) {
		channel = channelBuilder.build();
		asyncStub = MessageSourceGrpc.newStub(channel);
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	public List<String> send() throws Exception {
		return send(message -> new String(message.getPayload().toByteArray()));
	}

	public <T> List<T> send(Function<Message, T> xform) throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		List<T> value = new ArrayList<>();
		Trigger trigger = Trigger.getDefaultInstance();
		asyncStub.call(trigger, new StreamObserver<Message>() {

			@Override
			public void onNext(Message message) {
				logger.info("Message: " + message);
				value.add(xform.apply(message));
			}

			@Override
			public void onError(Throwable t) {
				logger.error("Error", t);
				latch.countDown();
			}

			@Override
			public void onCompleted() {
				logger.info("Finished");
				latch.countDown();
			}
		});
		latch.await(10, TimeUnit.SECONDS);
		return value;
	}
}
