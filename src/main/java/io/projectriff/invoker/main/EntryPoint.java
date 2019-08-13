package io.projectriff.invoker.main;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.projectriff.invoker.rpc.StartFrame;
import io.projectriff.invoker.server.GrpcServerAdapter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.deployer.FunctionDeployerConfiguration;
import org.springframework.cloud.function.deployer.FunctionProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * This is the main entry point for the java function invoker.
 * This sets up an application context with the whole Spring Cloud Function infrastructure (thanks to auto-configuration)
 * set up, pointing to the user function (via correctly set {@link FunctionProperties} ConfigurationProperties.
 * Then exposes a gRPC server adapting this function to the riff RPC protocol (muxing/de-muxing input and output values
 * over a single streaming channel). Marshalling and unmarshalling of byte encoded values is performed by Spring Cloud Function
 * itself, according to the incoming {@code Content-Type} header and the {@link StartFrame#getExpectedContentTypesList() expectedContentType} fields.
 *
 * @author Eric Bottard
 */
@SpringBootApplication
public class EntryPoint {

    public static void main(String[] args) throws InterruptedException, IOException {

        ApplicationContext context = SpringApplication.run(EntryPoint.class, args);

        Object o = new Object();
        synchronized (o) {
            o.wait();
        }

    }

    @Bean
    public GrpcServerAdapter adapter(FunctionCatalog functionCatalog, FunctionInspector functionInspector, FunctionProperties functionProperties) {
        return new GrpcServerAdapter(
                functionCatalog,
                functionInspector,
                functionProperties.getFunctionName()
        );
    }

    @Bean
	public SmartLifecycle server(GrpcServerAdapter adapter) {
        Server server = ServerBuilder.forPort(8081).addService(adapter).build();
        return new SmartLifecycle() {

            private volatile boolean running;

            @Override
            public void start() {
                try {
                    server.start();
                    running = true;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void stop() {
                server.shutdown();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }

}
