package com.acme;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;

public class TupledTruthStreamingConsumer implements Consumer<Tuple2<Flux<Integer>, Flux<String>>> {

    private final File sink1;
    private final File sink2;

    public TupledTruthStreamingConsumer() {
        sink1 = initTempFile("TupledTruthStreamingConsumer1");
        sink2 = initTempFile("TupledTruthStreamingConsumer2");
    }

    @Override
    public void accept(Tuple2<Flux<Integer>, Flux<String>> args) {
        args.getT1().subscribe(consumerOf(sink1));
        args.getT2().subscribe(consumerOf(sink2));
    }

    private File initTempFile(String name) {
        File sink = new File(System.getProperty("java.io.tmpdir"), name);
        if (sink.exists() && !sink.delete()) {
            throw new RuntimeException(String.format("File %s could not be deleted", sink.getAbsolutePath()));
        }
        return sink;
    }

    private Consumer<Object> consumerOf(File sink) {
        return value -> {
            try (FileWriter fileWriter = new FileWriter(sink, true)) {
                fileWriter.write(value.toString());
                fileWriter.write("\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
