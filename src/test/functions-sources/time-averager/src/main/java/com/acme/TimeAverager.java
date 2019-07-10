package com.acme;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.math.MathFlux;

/**
 * An example of function that already takes Flux and returns Flux.
 */
public class TimeAverager implements Function<Flux<Float>, Flux<Float>> {

	@Override
	public Flux<Float> apply(Flux<Float> floatFlux) {
		return floatFlux.window(Duration.ofSeconds(5)).flatMap(MathFlux::averageFloat);
	}
}
