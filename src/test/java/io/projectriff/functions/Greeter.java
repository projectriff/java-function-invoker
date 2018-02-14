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

package io.projectriff.functions;

import java.util.function.Function;

import reactor.core.publisher.Flux;

public class Greeter implements Function<Flux<Foo>, Flux<Bar>> {
	@Override
	public Flux<Bar> apply(Flux<Foo> foos) {
		return foos.map(foo -> new Bar("Hello " + foo.getValue()));
	}
}

class Foo {
	private String value;

	public Foo() {
	}

	public Foo(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}

class Bar {
	private String value;

	public Bar() {
	}

	public Bar(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}