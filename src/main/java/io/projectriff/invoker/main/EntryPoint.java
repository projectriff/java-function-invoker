package io.projectriff.invoker.main;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.projectriff.invoker.server.GrpcServerAdapter;
import org.springframework.cloud.function.deployer.FunctionDeployerBootstrap;

import java.io.IOException;

/**
 * @author Eric Bottard
 */
public class EntryPoint {

    public static void main(String[] args) throws InterruptedException, IOException {
        //String[] args = new String[] {"--spring.cloud.function.location=target/it/bootjar/target/bootjar-0.0.1.BUILD-SNAPSHOT-exec.jar",
        //		"--spring.cloud.function.function-class=function.example.UpperCaseFunction"};

        MyContainer invokerByClass = FunctionDeployerBootstrap.instance(args).run(MyContainer.class, args);

		GrpcServerAdapter adapter = new GrpcServerAdapter(
				invokerByClass.getFunctionCatalog(),
				invokerByClass.getFunctionInspector(),
				invokerByClass.getFunctionProperties().getFunctionName()
		);
		Server server = ServerBuilder.forPort(8081).addService(adapter).build();
		server.start();

	}

}
