package com.acme;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EncodeTest {

    @Test
    public void testFunction() {
        Flux<Integer> result = new Encode().apply(Flux.just(1, 1, 1, 0, 0, 0, 0, 1, 1));
        StepVerifier.create(result)
                .expectNext(3, 1)
                .expectNext(4, 0)
                .expectNext(2, 1)
                .verifyComplete();

    }
}
