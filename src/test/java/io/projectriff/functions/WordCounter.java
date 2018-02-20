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
package io.projectriff.functions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class WordCounter implements Function<Publisher<String>, Publisher<Map<String, Integer>>> {

	@Override
	public Publisher<Map<String, Integer>> apply(Publisher<String> words) {
		return Flux.from(words).window(Duration.ofSeconds(10))
				.flatMap(f -> f.flatMap(word -> Flux.fromArray(word.split("\\W")))
						.reduce(new HashMap<String, Integer>(), (map, word) -> {
							map.merge(word, 1, Integer::sum);
							return map;
						}));
	}

}
