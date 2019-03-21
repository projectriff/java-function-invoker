/*
 * Copyright 2017-2018 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.InMemoryFunctionCatalog;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { FunctionConfiguration.class, FunctionProperties.class,
		InMemoryFunctionCatalog.class })
@DirtiesContext
public abstract class FunctionConfigurationTests {

	@TestPropertySource(properties = {
			"function.uri=file:target/test-classes?handler=io.projectriff.functions.Doubler" })
	public static class SingleFunctionTests extends FunctionConfigurationTests {

		@Autowired
		private FunctionCatalog catalog;

		@Test
		public void testDouble() {
			Function<Integer, Integer> function = catalog.lookup(Function.class,
					"function0");
			assertThat(function.apply(2), is(4));
		}
	}

	@TestPropertySource(properties = { "function.uri=file:target/test-classes?handler="
			+ "io.projectriff.functions.NumberEmitter,"
			+ "io.projectriff.functions.Frenchizer" })
	public static class SupplierCompositionTests extends FunctionConfigurationTests {

		@Autowired
		private FunctionCatalog catalog;

		@Test
		public void testSupplier() {
			Supplier<Integer> function = catalog.lookup(Supplier.class, "function0");
			assertThat(function.get(), is(1));
		}

		@Test
		public void testFunction() {
			Function<Integer, String> function = catalog.lookup(Function.class,
					"function1");
			assertThat(function.apply(1), is("un"));
		}

	}

	@TestPropertySource(properties = { "function.uri=file:target/test-classes?handler="
			+ "io.projectriff.functions.Doubler,"
			+ "io.projectriff.functions.Frenchizer" })
	public static class FunctionCompositionTests extends FunctionConfigurationTests {

		@Autowired
		private FunctionCatalog catalog;

		@Test
		public void testFunction() {
			Function<Integer, Integer> function = catalog.lookup(Function.class,
					"function0");
			assertThat(function.apply(2), is(4));
		}

		@Test
		public void testThen() {
			Function<Integer, String> function = catalog.lookup(Function.class,
					"function1");
			assertThat(function.apply(4), is("quatre"));
		}
	}

	@TestPropertySource(properties = { "function.uri=file:target/test-classes?handler="
			+ "io.projectriff.functions.Frenchizer,"
			+ "io.projectriff.functions.Printer" })
	public static class ConsumerCompositionTests extends FunctionConfigurationTests {

		@Rule
		public OutputCapture capture = new OutputCapture();

		@Autowired
		private FunctionCatalog catalog;

		@Test
		public void testConsumer() {
			Consumer<String> function = catalog.lookup(Consumer.class, "function1");
			function.accept("2");
			capture.expect(containsString("Seen 2"));
		}
	}
}
