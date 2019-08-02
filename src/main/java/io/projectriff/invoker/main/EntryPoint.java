package io.projectriff.invoker.main;

import io.projectriff.invoker.server.JavaFunctionInvoker;
import org.springframework.cloud.function.deployer.ApplicationBootstrap;
import reactor.core.publisher.Hooks;

/**
 * Main entry point for the java function invoker app.
 *
 * <p>Living in a separate package so that it doesn't get picked-up again by {@link ApplicationBootstrap}.</p>
 *
 * @author Eric Bottard
 */
public class EntryPoint {

	public static void main(String[] args) throws InterruptedException {
		Hooks.onOperatorDebug();
		new ApplicationBootstrap().run(JavaFunctionInvoker.class);
		Object o = new Object();
		synchronized (o) {
			o.wait();
		}
	}

}
