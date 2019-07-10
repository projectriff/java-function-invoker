package com.acme;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

public class Encode implements Function<Flux<Integer>, Flux<Integer>[]> {

	public Flux<Integer>[] apply(Flux<Integer> input) {
		return new Flux[] {input.
			compose(Encode::detectRun).
			flatMap(buf -> Flux.just(buf.size(), buf.get(0)))};
	}
	
	private static Publisher<List<Integer>> detectRun(Flux<Integer> s) {
		return s.bufferUntil(new Predicate<Integer>() {
			Integer old;

			@Override
			public boolean test(Integer item) {
				try {
					return !item.equals(old);
				} finally {
					old = item;
				}
			}
		}, true);  // cut before item that terminates the buffer
	}
}
