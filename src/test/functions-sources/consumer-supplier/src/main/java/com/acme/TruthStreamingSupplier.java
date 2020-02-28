package com.acme;

import reactor.core.publisher.Flux;

import java.util.function.Supplier;

public class TruthStreamingSupplier implements Supplier<Flux<Integer>> {

    @Override
    public Flux<Integer> get() {
        return Flux.just(42, 41, 43);
    }
}
