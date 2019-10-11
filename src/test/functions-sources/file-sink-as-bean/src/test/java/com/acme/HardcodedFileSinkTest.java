package com.acme;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllLines;
import static org.assertj.core.api.Assertions.assertThat;

public class HardcodedFileSinkTest {

    private Consumer<Flux<Integer>> sink = new HardcodedFileSink();
    private File hardcodedFile = new File(System.getProperty("java.io.tmpdir"), "let-me-sink.txt");

    @Before
    @After
    public void cleanUp() throws IOException {
        Files.writeString(hardcodedFile.toPath(), "");
    }

    @Test
    public void writes_numbers_to_file() throws IOException {
        sink.accept(Flux.just(1, 2, 3));

        assertThat(readAllLines(hardcodedFile.toPath(), UTF_8))
                .containsExactly("1", "2", "3");
    }
}
