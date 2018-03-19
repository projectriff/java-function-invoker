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

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
public class GrpcSinkTests {

	private int port = SocketUtils.findAvailableTcpPort();

	@Rule
	public ExpectedException expected = ExpectedException.none();
	private JavaFunctionInvokerApplication runner;

	private GrpcTestClient client;

	@Before
	public void init() {
		runner = new JavaFunctionInvokerApplication();
		client = new GrpcTestClient("localhost", port);
	}

	@After
	public void close() throws Exception {
		if (runner != null) {
			runner.close();
		}
	}

	@Test
	public void fluxConsumer() throws Exception {
		runner.run("--server.port=0", "--grpc.port=" + port,
				"--function.uri=file:target/test-classes"
						+ "?handler=io.projectriff.functions.Logger");
		List<String> result = client.send("foo");
		assertThat(result).isEmpty();
		ApplicationRunner runner = (ApplicationRunner) ReflectionTestUtils
				.getField(this.runner, "runner");
		assertThat(runner.containsBean("io.projectriff.functions.Logger")).isFalse();
	}
}
