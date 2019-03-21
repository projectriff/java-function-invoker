/*
 * Copyright 2016-2019 the original author or authors.
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author David Turanski
 *
 */
public class FunctionEnvironmentPostProcessorTests {

	private FunctionEnvironmentPostProcessor processor = new FunctionEnvironmentPostProcessor();

	private StandardEnvironment environment = new StandardEnvironment();

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test
	public void uriWithHandler() {
		TestPropertyValues.of("function.uri=file:target/test-classes?handler=foo")
				.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isEqualTo("foo");
	}

	@Test
	public void uriWithHandlerAndMain() {
		TestPropertyValues
				.of("function.uri=file:target/test-classes?handler=foo&main=FooFuncs")
				.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isEqualTo("foo");
		assertThat(environment.getProperty("function.main")).isEqualTo("FooFuncs");
	}

	@Test
	public void uriWithMainOnly() {
		TestPropertyValues
			.of("function.uri=file:target/test-classes?main=FooFuncs")
			.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
			.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isNull();
		assertThat(environment.getProperty("function.main")).isEqualTo("FooFuncs");
	}

	@Test
	public void uriWithNoHandler() {
		TestPropertyValues.of("function.uri=file:target/test-classes")
				.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isNull();
	}

	@Test
	public void uriWithNoEmptyHandler() {
		TestPropertyValues.of("function.uri=file:target/test-classes?handler=")
				.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
				.isEqualTo("file:target/test-classes");
		assertThat(environment.getProperty("function.bean")).isNull();
	}

	@Test
	public void uriWithNoProtocol() {
		TestPropertyValues.of("function.uri=target/test-classes")
			.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
			.isEqualTo("file:target/test-classes");
	}

	@Test
	public void uriWithAppProtocol() {
		TestPropertyValues.of("function.uri=app:classpath?handler=io.projectriff.functions.Doubler")
			.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
			.isEqualTo("app:classpath");
		assertThat(environment.getProperty("function.bean"))
			.isEqualTo("io.projectriff.functions.Doubler");
	}

	@Test
	public void invalidUrl() {
		TestPropertyValues
			.of("function.uri=file:target/test-classes&handler=foo&main=FooFuncs")
			.applyTo(environment);
		processor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("function.location"))
			.isEqualTo("file:target/test-classes&handler=foo&main=FooFuncs");
		assertThat(environment.getProperty("function.bean")).isNull();
		assertThat(environment.getProperty("function.main")).isNull();
	}

}
