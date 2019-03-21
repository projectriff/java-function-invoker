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

import java.io.File;
import java.net.URI;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class FatJarPofTests {

	private TestRestTemplate rest;
	private int port = SocketUtils.findAvailableTcpPort();

	private File sampleJar;

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

	@Before
	public void check() {
		sampleJar = new File(
				"target/it/pof/target/function-sample-pof-1.0.0.BUILD-SNAPSHOT-exec.jar");
		Assume.assumeTrue("Sample jar does not exist: " + sampleJar, sampleJar.exists());
	}

	@Test
	public void fatJar() throws Exception {
		runner.run("--server.port=" + port,
				"--function.uri=" + sampleJar.toURI() + "?handler=functions.Greeter");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("World"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("Hello World");
	}

	@Test
	public void fatJarWithMain() throws Exception {
		runner.run("--server.port=" + port, "--function.uri=" + sampleJar.toURI()
				+ "?handler=functions.Greeter&main=functions.Application");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("World"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("Hello World");
	}

	@Test
	public void libJar() throws Exception {
		String uri = sampleJar.toURI().toString();
		uri = uri.replaceAll("-exec", "");
		runner.run("--server.port=" + port,
				"--function.uri=" + uri + "?handler=functions.Greeter");
		ResponseEntity<String> result = rest
				.exchange(
						RequestEntity.post(new URI("http://localhost:" + port + "/"))
								.contentType(MediaType.TEXT_PLAIN).body("World"),
						String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("Hello World");
	}

}
