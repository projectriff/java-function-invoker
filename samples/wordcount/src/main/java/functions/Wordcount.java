package functions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;

public class Wordcount implements Function<Flux<String>, Flux<Map<String, Integer>>> {

	@Override
	public Flux<Map<String, Integer>> apply(Flux<String> words) {
		return Flux.from(words.collect(
				HashMap::new, (map, word) -> map.merge(word, 1, Integer::sum)));
	}
}
