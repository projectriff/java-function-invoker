package com.acme;

import reactor.core.publisher.Flux;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.function.Consumer;

public class TruthStreamingConsumer implements Consumer<Flux<Integer>> {

    private final File sink;

    public TruthStreamingConsumer() {
        sink = new File(System.getProperty("java.io.tmpdir"), "TruthStreamingConsumer");
        if (sink.exists() && !sink.delete()) {
            throw new RuntimeException(String.format("File %s could not be deleted", sink.getAbsolutePath()));
        }
    }

    @Override
    public void accept(Flux<Integer> integers) {
        integers.subscribe(this::write);
    }

    private void write(Integer integer) {
        try (FileWriter fileWriter = new FileWriter(sink, true)) {
            fileWriter.write(integer.toString());
            fileWriter.write("\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
