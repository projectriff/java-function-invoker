package com.acme;

import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import static java.nio.file.Files.writeString;
import static java.nio.file.StandardOpenOption.APPEND;

public class HardcodedFileSink implements Consumer<Flux<Integer>> {

    private final Path file;

    public HardcodedFileSink() {
        file = initFile(new File(System.getProperty("java.io.tmpdir"), "let-me-sink.txt"));
    }

    @Override
    public void accept(Flux<Integer> integerFlux) {
        integerFlux.log().subscribe(integer -> tryAppend(String.format("%d%n", integer)));
    }

    private static Path initFile(File file) {
        try {
            Path path = file.toPath();
            writeString(path, "");
            return path;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void tryAppend(String contents) {
        try {
            writeString(file, contents, APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
