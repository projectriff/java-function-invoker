package com.acme;

import java.util.function.Supplier;

public class TruthSupplier implements Supplier<Integer> {

    @Override
    public Integer get() {
        return 42;
    }
}
