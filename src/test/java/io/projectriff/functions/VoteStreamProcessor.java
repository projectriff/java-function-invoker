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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class VoteStreamProcessor
		implements Function<Flux<String>, Flux<Map<String, Object>>> {

	@Override
	public Flux<Map<String, Object>> apply(Flux<String> words) {
		return Flux.merge(intervals(words), windows(words));
	}

	public Flux<Map<String, Object>> intervals(Flux<String> words) {
		return words.window(Duration.ofSeconds(2))
				.concatMap(w -> w.collect(VoteAggregate::new, VoteAggregate::sum)
						.map(VoteAggregate::intervalMap));
	}

	public Flux<Map<String, Object>> windows(Flux<String> words) {
		return words.window(Duration.ofSeconds(60), Duration.ofSeconds(2))
				.concatMap(w -> w.collect(VoteAggregate::new, VoteAggregate::sum)
						.map(VoteAggregate::windowMap), Integer.MAX_VALUE);
	}

	class VoteAggregate {
		long timestamp = System.currentTimeMillis();
		Map<String, Integer> votes = new HashMap<>();

		Map<String, Object> intervalMap() {
			Map<String, Object> map = map();
			map.put("_list", "demo:votes-log");
			return map;
		}

		Map<String, Object> windowMap() {
			Map<String, Object> map = map();
			map.put("_list", "demo:votes-windows");
			return map;
		}

		Map<String, Object> map() {
			Map<String, Object> map = new HashMap<>(votes);
			map.put("_time", timestamp);
			return map;
		}

		void sum(String word) {
			votes.merge(word, 1, Integer::sum);
		}
	}
}
