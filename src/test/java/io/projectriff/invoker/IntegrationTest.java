package io.projectriff.invoker;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.projectriff.invoker.client.FunctionClient;
import org.junit.*;
import org.junit.rules.TestName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Integration tests for the invoker. Spins up an external java process pointing to several functions, similar to the
 * way the invoker is meant to be used. Requires that the invoker be built as a boot uberjar first (eg using {@code mvn clean package}).
 *
 * @author Eric Bottard
 */
public class IntegrationTest {

    private static String javaExecutable;

    private static String invokerJar;

    private ProcessBuilder processBuilder;

    private Process process;

    @Rule
    public TestName testName = new TestName();

    private ManagedChannel channel;

    @BeforeClass
    public static void locateJavaExecutable() {
        File exec = new File(System.getProperty("java.home"), "bin/java");
        if (exec.exists()) {
            javaExecutable = exec.getPath();
        } else {
            javaExecutable = "java";
        }
    }

    @BeforeClass
    public static void locateInvokerJar() {
        String[] targets = new File("target")
                .list((d, n) -> n.matches("java-function-invoker-\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?\\.jar"));
        if (targets.length != 1) {
            throw new RuntimeException("Could not locate java invoker jar in " + Arrays.asList(targets));
        }
        invokerJar = String.format("target%s%s", File.separator, targets[0]);
    }

    @Before
    public void prepareProcess() {
        processBuilder = new ProcessBuilder(javaExecutable, "-jar", /*"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",*/ invokerJar);
        processBuilder.redirectOutput(new File(String.format("target%s%s.out", File.separator, testName.getMethodName())));
        processBuilder.redirectError(new File(String.format("target%s%s.err", File.separator, testName.getMethodName())));
        processBuilder.environment().clear();
        processBuilder.environment().put("PATH", System.getenv("PATH"));
    }

    @After
    public void shutdownInvoker() throws InterruptedException {
        channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        process.destroy();
        process.waitFor();
    }

    /*
     * This tests a function that uses reactor types in its signature.
     */
    @Test
    public void testStreamingFunction() throws Exception {
        setFunctionLocation("encode-1.0.0-boot");
        setFunctionClass("com.acme.Encode");
        process = processBuilder.start();

        Function<Flux<Integer>, Flux<Integer>> fn = FunctionClient.of(connect(), Integer.class);

        Flux<Integer> result = fn.apply(Flux.just(1, 1, 1, 0, 0, 0, 0, 1, 1));

        StepVerifier.create(result)
                .expectNext(3, 1)
                .expectNext(4, 0)
                .expectNext(2, 1)
                .verifyComplete();
    }

    /*
     * This tests a function that uses reactor types in its signature
     * and shares fluxes in its implementation
     */
    @Test
    public void testStreamingFunctionSharingFluxes() throws Exception {
        setFunctionLocation("repeater-1.0.0-boot");
        setFunctionClass("com.acme.Repeater");
        process = processBuilder.start();

        Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<String>, Flux<Integer>>> fn = FunctionClient.of(connect(), String.class, Integer.class);

        Tuple2<Flux<String>, Flux<Integer>> result = fn.apply(Tuples.of(
                Flux.just("one", "two", "three"),
                Flux.just(1, 2, 3, 4, 5, 6)
        ));

        StepVerifier.create((Flux<String>) result.getT1())
                .expectNext("one")
                .expectNext("two")
                .expectNext("two")
                .expectNext("three")
                .expectNext("three")
                .expectNext("three")
                .verifyComplete();
        StepVerifier.create((Flux<Integer>) result.getT2())
                .expectNext(3)
                .expectNext(5)
                .expectNext(7)
                .expectNext(9)
                .expectNext(11)
                .expectNext(6)
                .verifyComplete();
    }

    /*
     * This tests {@link FunctionClient} rather than the invoker: Tests that even if a particular returned Flux has no
     * data, other Fluxes still emit data. A problem could be caused by the groupBy+take implementation of FunctionClient.
     */
    @Test
    public void testFunctionProxyDoesNotStallOnEmptyResponse() throws Exception {
        setFunctionLocation("repeater-1.0.0-boot");
        setFunctionClass("com.acme.Repeater");
        process = processBuilder.start();

        Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<String>, Flux<Integer>>> fn = FunctionClient.of(connect(), String.class, Integer.class);

        Tuple2<Flux<String>, Flux<Integer>> result = fn.apply(Tuples.of(
                Flux.just("one", "two", "three"),
                Flux.just(0, 0, 0) // Enough zeroes to trigger 3x a sum (of 2, 2 and 1 zeroes), but emits zero times the words above (via zip())
        ));

        StepVerifier.create(result.getT1())
                .verifyComplete();
        StepVerifier.create(result.getT2())
                .expectNext(0)
                .expectNext(0)
                .expectNext(0)
                .verifyComplete();
    }

    /*
     * This tests a function that doesn't require special types and is packaged as a plain old
     * jar.
     */
    @Test
    public void testSimplestJarFunction() throws Exception {
        setFunctionLocation("hundred-divider-1.0.0");
        setFunctionClass("com.acme.HundredDivider");
        process = processBuilder.start();


        Function<Flux<Integer>, Flux<Integer>> fn = FunctionClient.of(connect(), Integer.class);

        Flux<Integer> response = fn.apply(Flux.just(1, 2, 4));
        StepVerifier.create(response)
                .expectNext(100, 50, 25)
                .verifyComplete();

    }

    /*
     * This tests a function that accepts multi i/o, packaged as a spring bean, in a boot uberjar.
     */
    @Test
    public void testMulti() throws Exception {
        setFunctionLocation("repeater-as-bean-1.0.0-boot");
        setFunctionName("fn");
        process = processBuilder.start();

        Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<Double>, Flux<String>>> fn = FunctionClient.of(connect(), Double.class, String.class);

        Tuple2<Flux<Double>, Flux<String>> response = fn.apply(
                Tuples.of(
                        Flux.just("a", "bb", "ccc"),
                        Flux.just(1, 2, 3)
                )
        );
        StepVerifier.create(response.getT1())
                .expectNext(1.5d, 2.5d, 3.0d)
                .verifyComplete();
        StepVerifier.create(response.getT2())
                .expectNext("a")
                .expectNext("bb", "bb")
                .expectNext("ccc", "ccc", "ccc")
                .verifyComplete();

    }

    /*
     * This tests the client triggering an onError() event.
     */
    @Test
    //@Ignore
    public void testClientError() throws Exception {
        setFunctionLocation("hundred-divider-1.0.0");
        setFunctionClass("com.acme.HundredDivider");
        process = processBuilder.start();

        Function<Flux<Integer>, Flux<Integer>> function = FunctionClient.of(connect(), Integer.class);


        Flux<Integer> input = Flux.concat(
                Flux.just(1, 2, 3),
                Flux.error(new RuntimeException("Boom")));
        // TODO: revise semantics? Using Duraction < 100ms fails immediately
        input = Flux.interval(Duration.ofMillis(100)).flatMap(i -> i == 3 ? Flux.error(new RuntimeException("Boom")) : Flux.just(i.intValue() + 1));
        Flux<Integer> response = function.apply(input);
        StepVerifier.create(response)
                .expectNext(100, 50, 33)
                .verifyErrorMatches(t -> (t instanceof StatusRuntimeException) && ((StatusRuntimeException) t).getStatus().getCode() == Status.Code.CANCELLED);

    }

    /*
     * This tests a runtime error happening in the function computation.
     */
    @Test
    public void testFunctionError() throws Exception {
        setFunctionLocation("hundred-divider-1.0.0");
        setFunctionClass("com.acme.HundredDivider");
        process = processBuilder.start();

        Function<Flux<Integer>, Flux<Integer>> function = FunctionClient.of(connect(), Integer.class);

        Flux<Integer> response = function.apply(Flux.just(1, 2, 0));
        StepVerifier.create(response)
                .expectNext(100, 50)
                .verifyErrorMatches(t -> (t instanceof StatusRuntimeException) && ((StatusRuntimeException) t).getStatus().getCode() == Status.Code.UNKNOWN);

    }

    /**
     * Waits for connectivity to the gRPC server then creates, sets and returns a Channel that can be used
     * to create a client.
     */
    private ManagedChannel connect() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            try {
                new Socket().connect(new InetSocketAddress("localhost", 8081));
                break;
            } catch (IOException e) {
                Thread.sleep(500);
            }
        }
        channel = ManagedChannelBuilder.forAddress("localhost", 8081)
                .usePlaintext()
                .build();
        return channel;
    }

    private void setFunctionClass(String value) {
        processBuilder.command().add("--spring.cloud.function.function-class=" + value);
    }

    private void setFunctionName(String value) {
        processBuilder.command().add("--spring.cloud.function.function-name=" + value);
    }

    private void setFunctionLocation(String jar) {
        processBuilder.command().add("--spring.cloud.function.location=" +
                new File(String.format("src/test/functions/%s.jar", jar)).getAbsolutePath());
    }

}
