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
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.resource.maven.MavenResourceLoader;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StreamUtils;

/**
 * Sets up infrastructure capable of instantiating a "functional" bean (whether Supplier,
 * Function or Consumer) loaded dynamically according to {@link FunctionProperties}.
 *
 * <p>
 * In case of multiple class names, composes functions together.
 * </p>
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Dave Syer
 */
@Configuration
@EnableConfigurationProperties
public class FunctionConfiguration {

	private static Log logger = LogFactory.getLog(FunctionConfiguration.class);

	@Autowired
	private FunctionRegistry registry;

	@Autowired
	private FunctionProperties properties;

	@Autowired
	private DelegatingResourceLoader delegatingResourceLoader;

	@Autowired
	private ConfigurableApplicationContext context;

	private BeanCreatorClassLoader functionClassLoader;

	private BeanCreator creator;

	@Bean
	@ConfigurationProperties("maven")
	public MavenProperties mavenProperties() {
		return new MavenProperties();
	}

	@Bean
	@ConditionalOnMissingBean(DelegatingResourceLoader.class)
	public DelegatingResourceLoader delegatingResourceLoader(
			MavenProperties mavenProperties) {
		Map<String, ResourceLoader> loaders = new HashMap<>();
		loaders.put(MavenResource.URI_SCHEME, new MavenResourceLoader(mavenProperties));
		return new DelegatingResourceLoader(loaders);
	}

	/**
	 * Registers a function for each of the function classes passed into the
	 * {@link FunctionProperties}. They are named sequentially "function0", "function1",
	 * etc. The instances are created in an isolated class loader, so the jar they are
	 * packed in has to define all the dependencies (except core JDK).
	 */
	@PostConstruct
	public void init() {
		URL[] urls = Arrays.stream(properties.getJarLocation())
				.flatMap(toResourceURL(delegatingResourceLoader)).toArray(URL[]::new);

		try {
			this.creator = new BeanCreator(expand(urls));
			this.creator.run(properties.getMainClassName());
			Arrays.stream(properties.getClassName()).map(this.creator::create)
					.sequential().forEach(this.creator::register);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot create functions", e);
		}
	}

	private URL[] expand(URL[] urls) {
		List<URL> result = new ArrayList<>();
		for (URL url : urls) {
			result.addAll(expand(url));
		}
		return result.toArray(new URL[0]);
	}
	
	private List<URL> expand(URL url) {
		if (!"file".equals(url.getProtocol())) {
			return Collections.singletonList(url);
		}
		if (!url.toString().endsWith(".jar")) {
			return Collections.singletonList(url);
		}
		try {
			JarFileArchive archive = new JarFileArchive(new File(url.toURI()));
			return Arrays.asList(new ComputeLauncher(archive).getClassLoaderUrls());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot create class loader for " + url, e);
		}
	}

	@PreDestroy
	public void close() {
		if (this.creator != null) {
			this.creator.close();
		}
		if (this.functionClassLoader != null) {
			try {
				this.functionClassLoader.close();
				this.functionClassLoader = null;
				Runtime.getRuntime().gc();
			}
			catch (IOException e) {
				throw new IllegalStateException("Cannot close function class loader", e);
			}
		}
	}

	private Function<String, Stream<URL>> toResourceURL(
			DelegatingResourceLoader resourceLoader) {
		return l -> {
			if (l.equals("app:classpath")) {
				return Stream
						.of(((URLClassLoader) getClass().getClassLoader()).getURLs());
			}
			try {
				return Stream.of(resourceLoader.getResource(l).getFile().toURI().toURL());
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};
	}

	private class ComputeLauncher extends JarLauncher {

		public ComputeLauncher(JarFileArchive archive) {
			super(archive);
		}

		public URL[] getClassLoaderUrls() throws Exception {
			List<Archive> archives = getClassPathArchives();
			if (archives.isEmpty()) {
				return new URL[] { getArchive().getUrl() };
			}
			return archives.stream().map(archive -> {
				try {
					return archive.getUrl();
				}
				catch (MalformedURLException e) {
					throw new IllegalStateException("Bad URL: " + archive, e);
				}
			}).collect(Collectors.toList()).toArray(new URL[0]);
		}

	}

	/**
	 * Encapsulates the bean and spring application context creation concerns for
	 * functions. Creates a single application context if <code>run()</code> is called
	 * with a non-null main class, and then uses it to lookup functions (by name and then
	 * by type).
	 */
	private class BeanCreator {

		private AtomicInteger counter = new AtomicInteger(0);
		private ApplicationRunner runner;

		public BeanCreator(URL[] urls) {
			functionClassLoader = new BeanCreatorClassLoader(urls,
					getClass().getClassLoader().getParent());
		}

		public void run(String main) {
			if (main == null) {
				return;
			}
			if (ClassUtils.isPresent(SpringApplication.class.getName(),
					functionClassLoader)) {
				logger.info("SpringApplication available. Bootstrapping: " + main);
				ClassLoader contextClassLoader = ClassUtils
						.overrideThreadContextClassLoader(functionClassLoader);
				try {
					ApplicationRunner runner = new ApplicationRunner(functionClassLoader,
							main);
					// TODO: make the runtime properties configurable
					runner.run("--spring.main.webEnvironment=false",
							"--spring.cloud.stream.enabled=false",
							"--spring.main.bannerMode=OFF",
							"--spring.main.webApplicationType=none");
					this.runner = runner;
				}
				finally {
					ClassUtils.overrideThreadContextClassLoader(contextClassLoader);
				}
			}
			else {
				throw new IllegalStateException(
						"SpringApplication not available and main class requested: "
								+ main);
			}
		}

		public Object create(String type) {
			ClassLoader contextClassLoader = ClassUtils
					.overrideThreadContextClassLoader(functionClassLoader);
			AutowireCapableBeanFactory factory = context.getAutowireCapableBeanFactory();
			try {
				if (this.runner != null) {
					return this.runner.getBean(type);
				}
				logger.info("No main class provided. Instantiating: " + type);
				return factory.createBean(
						ClassUtils.resolveClassName(type, functionClassLoader));
			}
			finally {
				ClassUtils.overrideThreadContextClassLoader(contextClassLoader);
			}
		}

		public void register(Object bean) {
			FunctionRegistration<Object> registration = new FunctionRegistration<Object>(
					bean).names("function" + counter.getAndIncrement());
			if (this.runner != null) {
				if (this.runner.containsBean(FunctionInspector.class.getName())) {
					Object inspector = this.runner
							.getBean(FunctionInspector.class.getName());
					Class<?> input = (Class<?>) this.runner.evaluate(
							"getInputType(#function)", inspector, "function", bean);
					FunctionType type = FunctionType.from(input);
					Class<?> output = (Class<?>) this.runner.evaluate(
							"getOutputType(#function)", inspector, "function", bean);
					type = type.to(output);
					if (((Boolean) this.runner.evaluate("isMessage(#function)", inspector,
							"function", bean))) {
						type = type.message();
					}
					Class<?> wrapper = (Class<?>) this.runner.evaluate(
							"getInputWrapper(#function)", inspector, "function", bean);
					if (FunctionType.isWrapper(wrapper)) {
						type = type.wrap(wrapper);
					}
					registration.type(type.getType());
				}
			}
			registry.register(registration);
		}

		public void close() {
			if (this.runner != null) {
				this.runner.close();
			}
		}

	}

	private static final class BeanCreatorClassLoader extends URLClassLoader {
		private BeanCreatorClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve)
				throws ClassNotFoundException {
			try {
				return super.loadClass(name, resolve);
			}
			catch (ClassNotFoundException e) {
				if (name.contains(ContextRunner.class.getName())) {
					// Special case for the ContextRunner. We can re-use the bytes for it,
					// and the function jar doesn't have to include them since it is only
					// used here.
					byte[] bytes;
					try {
						bytes = StreamUtils.copyToByteArray(
								getClass().getClassLoader().getResourceAsStream(
										ClassUtils.convertClassNameToResourcePath(name)
												+ ".class"));
						return defineClass(name, bytes, 0, bytes.length);
					}
					catch (IOException ex) {
						throw new ClassNotFoundException(
								"Cannot find runner class: " + name, ex);
					}
				}
				throw e;
			}
		}
	}
}
