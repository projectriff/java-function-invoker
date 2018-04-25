/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.projectriff.invoker;

import io.projectriff.functions.Doubler;
import io.projectriff.functions.FunctionApp;

import org.junit.Test;

import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.cloud.function.deployer.ApplicationRunner;
import org.springframework.cloud.function.deployer.EnableFunctionDeployer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
public class ApplicationRunnerTests {

	@Test
	public void startEvaluateAndStop() {
		ApplicationRunner runner = new ApplicationRunner(getClass().getClassLoader(),
				FunctionApp.class.getName());
		runner.run("--spring.main.webEnvironment=false");
		assertThat(runner.containsBean(Doubler.class.getName())).isTrue();
		assertThat(runner.getBean(Doubler.class.getName())).isNotNull();
		assertThat(runner
				.containsBean(TomcatEmbeddedServletContainerFactory.class.getName()))
						.isFalse();
		runner.close();
	}

	@Test
	public void grpcProtocol() {
		ApplicationRunner runner = new ApplicationRunner(getClass().getClassLoader(),
				App.class.getName());
		runner.run("--riff.function.invoker.protocol=grpc",
				"--function.uri=app:classpath?handler=io.projectriff.functions.Doubler", "--grpc.port=0");
		assertThat(runner.containsBean(GrpcConfiguration.class.getName())).isTrue();
		assertThat(runner
				.containsBean(TomcatEmbeddedServletContainerFactory.class.getName()))
						.isFalse();
		runner.close();
	}

	@Test
	public void httpProtocol() {
		ApplicationRunner runner = new ApplicationRunner(getClass().getClassLoader(),
				App.class.getName());
		runner.run("--riff.function.invoker.protocol=http",
				"--function.uri=app:classpath?handler=io.projectriff.functions.Doubler", "--server.port=0");
		assertThat(runner.containsBean(GrpcConfiguration.class.getName())).isFalse();
		assertThat(runner
				.containsBean(TomcatEmbeddedServletContainerFactory.class.getName()))
						.isTrue();
		runner.close();
	}

	@Configuration
	@EnableFunctionDeployer
	@Import({ FunctionApp.class, GrpcConfiguration.class })
	public static class App {
	}
}
