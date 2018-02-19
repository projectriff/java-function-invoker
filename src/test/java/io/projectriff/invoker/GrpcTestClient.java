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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.projectriff.grpc.function.FunctionProtos.Message;
import io.projectriff.grpc.function.FunctionProtos.Message.HeaderValue;
import io.projectriff.grpc.function.MessageFunctionGrpc;
import io.projectriff.grpc.function.MessageFunctionGrpc.MessageFunctionStub;

import com.google.protobuf.ByteString;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.MediaType;

/**
 * @author Dave Syer
 *
 */
public class GrpcTestClient {
	private final Log logger = LogFactory.getLog(GrpcTestClient.class);

	private final ManagedChannel channel;
	private final MessageFunctionStub asyncStub;

	public GrpcTestClient(String host, int port) {
		this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true));
	}

	public GrpcTestClient(ManagedChannelBuilder<?> channelBuilder) {
		channel = channelBuilder.build();
		asyncStub = MessageFunctionGrpc.newStub(channel);
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	public List<String> send(String... payloads) throws Exception {
		return send(MediaType.TEXT_PLAIN, payloads);
	}

	public List<String> send(MediaType mediaType, String... payloads) throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		List<String> value = new ArrayList<>();
		StreamObserver<Message> obsvr = asyncStub.call(new StreamObserver<Message>() {

			@Override
			public void onNext(Message message) {
				logger.info("Message: " + message);
				value.add(new String(message.getPayload().toByteArray()));
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
		try {
			String correlationId = UUID.randomUUID().toString();
			for (String payload : payloads) {
				obsvr.onNext(Message.newBuilder()
						.setPayload(
								ByteString.copyFrom(payload, Charset.defaultCharset()))
						.putHeaders("Content-Type",
								HeaderValue.newBuilder().addValues(mediaType.toString())
										.build())
						.putHeaders("correlationId",
								HeaderValue.newBuilder().addValues(correlationId).build())
						.build());
				Thread.sleep(100L);
			}
		}
		catch (Exception e) {
			obsvr.onError(e);
			throw e;
		}
		obsvr.onCompleted();
		latch.await(10, TimeUnit.SECONDS);
		return value;
	}
}
