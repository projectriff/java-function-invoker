/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.Collection;
import java.util.Map.Entry;

import io.projectriff.grpc.function.FunctionProtos.Message.Builder;
import io.projectriff.grpc.function.FunctionProtos.Message.HeaderValue;

import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Dave Syer
 *
 */
public class MessageConversionUtils {

	public static Message<byte[]> fromGrpc(
			io.projectriff.grpc.function.FunctionProtos.Message input) {
		MessageBuilder<byte[]> builder = MessageBuilder
				.withPayload(input.getPayload().toByteArray());
		for (Entry<String, HeaderValue> entry : input.getHeadersMap().entrySet()) {
			HeaderValue header = entry.getValue();
			if (header.getValuesCount() > 0) {
				Object value;
				ProtocolStringList list = header.getValuesList();
				if (list.size() == 1) {
					value = list.get(0);
				}
				else {
					value = list;
				}
				builder.setHeader(entry.getKey(), value);
			}
		}
		return builder.build();
	}

	public static io.projectriff.grpc.function.FunctionProtos.Message toGrpc(
			Message<byte[]> input) {
		Builder builder = io.projectriff.grpc.function.FunctionProtos.Message.newBuilder()
				.setPayload(ByteString.copyFrom(input.getPayload()));
		for (Entry<String, Object> entry : input.getHeaders().entrySet()) {
			if (MessageHeaders.ID.equals(entry.getKey())) {
				continue;
			}
			Object header = entry.getValue();
			HeaderValue.Builder headerBuilder = HeaderValue.newBuilder();
			if (header instanceof Collection) {
				Collection<?> list = (Collection<?>) header;
				for (Object object : list) {
					String value = object.toString();
					headerBuilder.addValues(value);
				}
			}
			else if (header != null) {
				String value = header.toString();
				headerBuilder.addValues(value);
			}
			builder.putHeaders(entry.getKey(), headerBuilder.build());
		}
		return builder.build();
	}

}
