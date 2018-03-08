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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Mark Fisher
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
@TestPropertySource(properties = {"grpc.port=0", "function.uri=file:target/test-classes"
		+ "?handler=io.projectriff.functions.Doubler,io.projectriff.functions.Frenchizer"})
public class ComposedJavaFunctionInvokerApplicationTests {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private GrpcConfiguration server;

	@Test
	public void grpc() throws Exception {
		GrpcTestClient client = new GrpcTestClient("localhost", server.getPort());
		List<String> result = client.send("2");
		assertThat(result).contains("quatre");
	}

	@Test
	public void http() throws Exception {
		ResponseEntity<String> result = rest.exchange(RequestEntity.post(new URI("/"))
				.contentType(MediaType.TEXT_PLAIN).body("2"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("quatre");
	}

}
