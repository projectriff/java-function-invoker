package com.acme;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.function.Supplier;

public class TupledTruthStreamingSupplier implements Supplier<Tuple2<Flux<Integer>, Flux<Boolean>>> {

    @Override
    public Tuple2<Flux<Integer>, Flux<Boolean>> get() {
        return Tuples.of(
                Flux.just(42, 2, 42),
                Flux.just(true, false, true, true)
        );
    }
}
