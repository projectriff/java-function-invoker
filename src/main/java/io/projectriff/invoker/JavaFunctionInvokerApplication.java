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

import java.io.IOException;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.deployer.ApplicationBootstrap;
import org.springframework.cloud.function.deployer.EnableFunctionDeployer;

/**
 * @author Mark Fisher
 * @author Dave Syer
 */
@SpringBootApplication
@EnableFunctionDeployer
public class JavaFunctionInvokerApplication {

	private ApplicationBootstrap bootstrap;

	public static void main(String[] args) throws IOException {
		new JavaFunctionInvokerApplication().run(args);
	}

	public void run(String... args) {
		if (bootstrap == null) {
			bootstrap = new ApplicationBootstrap();
			bootstrap.run(JavaFunctionInvokerApplication.class, args);
		}
	}

	public void close() {
		if (bootstrap != null) {
			bootstrap.close();
		}
	}

}
