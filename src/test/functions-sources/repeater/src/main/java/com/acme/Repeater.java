package com.acme;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;

import reactor.core.publisher.Flux;

public class Repeater implements BiFunction<Flux<String>, Flux<Integer>, Flux<?>[]> {

    private static final String[] numbers = new String[]{"zero", "one", "two", "three", "four", "five"};

    @Override
    public Flux<?>[] apply(Flux<String> stringFlux, Flux<Integer> integerFlux) {
        Flux<Integer> sharedIntFlux = integerFlux.publish().autoConnect(2);

        Flux<String> repeated = stringFlux.zipWith(sharedIntFlux)
                .flatMap(t -> Flux.fromIterable(Collections.nCopies(t.getT2(), t.getT1())));

        Flux<Integer> sum = sharedIntFlux.buffer(2, 1)
                .map(l -> l.stream().mapToInt(Integer::intValue).sum())
                .take(3);

        return new Flux<?>[]{repeated, sum};
    }




    public static void main(String[] args) throws IOException {
        Flux<String> strings = Flux.interval(Duration.ofMillis(5000L)).map(i -> numbers[i.intValue() % numbers.length]);
        Flux<Integer> ints = Flux.interval(Duration.ofMillis(6000L)).map(i -> i.intValue() % numbers.length);
        Arrays.stream(new Repeater().apply(strings, ints))
                .forEach(flux -> flux.subscribe(System.out::println));
        System.in.read();
    }

}
