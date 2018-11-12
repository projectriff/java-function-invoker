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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
			bootstrap.run(JavaFunctionInvokerApplication.class, args(args));
		}
	}

	private String[] args(String[] args) {
		List<String> list = new ArrayList<>(Arrays.asList(args));
		boolean functional = false;
		for (String arg : list) {
			if (arg.startsWith("--spring.functional.enabled")) {
				functional = true;
				break;
			}
		}
		if (!functional) {
			list.add("--spring.functional.enabled=false");
		}
		return list.toArray(new String[0]);
	}

	public void close() {
		if (bootstrap != null) {
			bootstrap.close();
		}
	}

}
