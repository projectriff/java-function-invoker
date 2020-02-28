package com.acme;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;

public class TruthConsumer implements Consumer<Integer> {

    private final File sink;

    public TruthConsumer() {
        sink = new File(System.getProperty("java.io.tmpdir"), "TruthConsumer");
        if (sink.exists() && !sink.delete()) {
            throw new RuntimeException(String.format("File %s could not be deleted", sink.getAbsolutePath()));
        }
    }

    @Override
    public void accept(Integer integer) {
        try (FileWriter fileWriter = new FileWriter(sink, true)) {
            fileWriter.write(integer.toString());
            fileWriter.write("\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
