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
package io.projectriff.functions;

import java.util.function.Consumer;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class Logger implements Consumer<Flux<String>> {

	@Override
	public void accept(Flux<String> input) {
		input.map(value -> "Hello " + value).doOnNext(System.out::println);
	}

}
