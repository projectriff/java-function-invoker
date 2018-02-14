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

import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
public class IsolatedTests {

	private TestRestTemplate rest;
	private int port = SocketUtils.findAvailableTcpPort();

	@Rule
	public ExpectedException expected = ExpectedException.none();
	private JavaFunctionInvokerApplication runner;

	@Before
	public void init() {
		runner = new JavaFunctionInvokerApplication();
		rest = new TestRestTemplate();
	}

	@After
	public void close() throws Exception {
		if (runner != null) {
			runner.close();
		}
	}

	@Test
	public void fluxFunctionNotIsolated() throws Exception {
		expected.expect(BeanCreationException.class);
		SpringApplication.run(JavaFunctionInvokerApplication.class,
				"--server.port=" + port, "--function.uri=file:target/test-classes"
						+ "?handler=io.projectriff.functions.FluxDoubler");
	}

	@Test
	public void fluxFunction() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=file:target/test-classes"
				+ "?handler=io.projectriff.functions.FluxDoubler");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("5"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		// Check single valued response in s-c-f
		assertThat(result.getBody()).isEqualTo("[10]");
		ApplicationRunner runner = (ApplicationRunner) ReflectionTestUtils
				.getField(this.runner, "runner");
		assertThat(runner.containsBean("io.projectriff.functions.FluxDoubler")).isFalse();
	}

	@Test
	public void messageFunction() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=file:target/test-classes,file:target/test-functions"
				+ "?handler=io.projectriff.functions.MessageGreeter");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("World"),
						String.class);
		assertThat(result.getBody()).contains("Hello");
		assertThat(result.getBody()).doesNotContain("Exception");
	}

	@Test
	public void fluxMessageFunction() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=file:target/test-classes,file:target/test-functions"
				+ "?handler=io.projectriff.functions.FluxMessageGreeter");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("World"),
						String.class);
		assertThat(result.getBody()).contains("Hello");
		assertThat(result.getBody()).doesNotContain("Exception");
	}

	@Test
	public void simpleFunction() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=file:target/test-classes"
				+ "?handler=io.projectriff.functions.Doubler");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("5"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("10");
	}

	@Test
	public void appClassPath() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=app:classpath?"
				+ "handler=io.projectriff.functions.SpringDoubler");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("5"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("10");
	}

	@Test
	public void mainClassBeanName() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=app:classpath?"
				+ "handler=myDoubler&" + "main=io.projectriff.functions.FunctionApp");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("5"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("10");
	}

	@Test
	public void mainClassBeanType() throws Exception {
		runner.run("--server.port=" + port,
				"--function.uri=app:classpath?"
						+ "handler=io.projectriff.functions.Doubler&"
						+ "main=io.projectriff.functions.FunctionApp");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("5"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("10");
	}
}
