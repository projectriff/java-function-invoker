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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

/**
 * @author Mark Fisher
 * @author Dave Syer
 */
@SpringBootApplication
public class JavaFunctionInvokerApplication {

	private static Log logger = LogFactory.getLog(JavaFunctionInvokerApplication.class);
	private ApplicationRunner runner;
	private URLClassLoader classLoader;

	public static void main(String[] args) throws IOException {
		if (JavaFunctionInvokerApplication.isolated(args)) {
			JavaFunctionInvokerApplication application = new JavaFunctionInvokerApplication();
			application.run(args);
		}
		else {
			SpringApplication.run(JavaFunctionInvokerApplication.class, args);
		}
	}

	public void run(String... args) {
		runner().run(args);
	}

	@PreDestroy
	public void close() throws Exception {
		if (this.runner != null) {
			this.runner.close();
		}
		if (this.classLoader != null) {
			this.classLoader.close();
		}
	}

	private ApplicationRunner runner() {
		if (this.runner == null) {
			synchronized (this) {
				if (this.runner == null) {
					this.classLoader = createClassLoader();
					this.runner = new ApplicationRunner(this.classLoader,
							JavaFunctionInvokerApplication.class.getName());
				}
			}
		}
		return this.runner;
	}

	private static boolean isolated(String[] args) {
		for (String arg : args) {
			if (arg.equals("--function.runner.isolated=false")) {
				return false;
			}
		}
		return true;
	}

	private URLClassLoader createClassLoader() {
		URL[] urls = findClassPath();
		if (urls.length == 1) {
			URL[] classpath = extractClasspath(urls[0]);
			if (classpath != null) {
				urls = classpath;
			}
		}
		List<URL> child = new ArrayList<>();
		List<URL> parent = new ArrayList<>();
		for (URL url : urls) {
			child.add(url);
		}
		for (URL url : urls) {
			if (isRoot(StringUtils.getFilename(clean(url.toString())))) {
				parent.add(url);
				child.remove(url);
			}
		}
		logger.debug("Parent: " + parent);
		logger.debug("Child: " + child);
		ClassLoader base = getClass().getClassLoader();
		if (!parent.isEmpty()) {
			base = new URLClassLoader(parent.toArray(new URL[0]), base.getParent());
		}
		return new URLClassLoader(child.toArray(new URL[0]), base);
	}

	private URL[] findClassPath() {
		ClassLoader base = getClass().getClassLoader();
		if (!(base instanceof URLClassLoader)) {
			try {
				// Guess the classpath, based on where we can resolve existing resources
				List<URL> list = Collections
						.list(getClass().getClassLoader().getResources("META-INF"));
				List<URL> result = new ArrayList<>();
				result.add(
						getClass().getProtectionDomain().getCodeSource().getLocation());
				for (URL url : list) {
					String path = url.toString();
					path = path.substring(0, path.length() - "/META-INF".length());
					if (path.endsWith("!")) {
						path = path + "/";
					}
					result.add(new URL(path));
				}
				return result.toArray(new URL[result.size()]);
			}
			catch (IOException e) {
				throw new IllegalStateException("Cannot find class path", e);
			}
		}
		else {
			@SuppressWarnings("resource")
			URLClassLoader urlClassLoader = (URLClassLoader) base;
			return urlClassLoader.getURLs();
		}
	}

	private boolean isRoot(String file) {
		return file.startsWith("reactor-core") || file.startsWith("reactive-streams");
	}

	private String clean(String jar) {
		// This works with fat jars like Spring Boot where the path elements look like
		// jar:file:...something.jar!/.
		return jar.endsWith("!/") ? jar.substring(0, jar.length() - 2) : jar;
	}

	private URL[] extractClasspath(URL url) {
		// This works for a jar indirection like in surefire and IntelliJ
		if (url.toString().endsWith(".jar")) {
			JarFile jar;
			try {
				jar = new JarFile(new File(url.toURI()));
				String path = jar.getManifest().getMainAttributes()
						.getValue("Class-Path");
				if (path != null) {
					List<URL> result = new ArrayList<>();
					for (String element : path.split(" ")) {
						result.add(new URL(element));
					}
					return result.toArray(new URL[0]);
				}
			}
			catch (Exception e) {
			}
		}
		return null;
	}
}
