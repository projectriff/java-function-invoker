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

import org.junit.Test;

import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class FunctionEnvironmentPostProcessorTests {

	private FunctionEnvironmentPostProcessor processor = new FunctionEnvironmentPostProcessor();

	private StandardEnvironment environment = new StandardEnvironment();

	@Test
	public void uriWithHandler() {
		EnvironmentTestUtils.addEnvironment(environment,
				"function.uri=file:target/test-classes?handler=foo");
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isEqualTo("foo");
	}

	@Test
	public void uriWithHandlerAndMain() {
		EnvironmentTestUtils.addEnvironment(environment,
				"function.uri=file:target/test-classes?handler=foo&main=FooFuncs");
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isEqualTo("foo");
		assertThat(environment.getProperty("function.main")).isEqualTo("FooFuncs");
	}

	@Test(expected = IllegalArgumentException.class)
	public void uriWithNoHandler() {
		EnvironmentTestUtils.addEnvironment(environment,
				"function.uri=file:target/test-classes");
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isNull();
	}

}
