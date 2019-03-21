/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.projectriff.invoker;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * Convert the function.uri into properties that can be consumed by the deployer library.
 *
 * @author Dave Syer
 * @author David Turanski
 */
public class FunctionEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String PROPERTY_SOURCE_NAME = "defaultProperties";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
		SpringApplication application) {

		/*
		 * Stub to resolve non-standard protocols.
		 */
		URLStreamHandlerFactory urlStreamHandlerFactory = protocol -> new URLStreamHandler() {
			@Override
			protected URLConnection openConnection(URL u) {
				return null;
			}
		};

		URL url;

		if (environment.containsProperty("function.uri")) {
			try {
				/*
				 * Protocol defaults to file:
				 */
				url = new URL(new URL("file:dummy"), environment.getProperty("function.uri"),
					urlStreamHandlerFactory.createURLStreamHandler("app"));
			}
			catch (MalformedURLException e) {
				throw new IllegalArgumentException(String.format("'function.uri' property %s is invalid",
					environment.getProperty("function.uri")));
			}

			Map<String, Object> map = new HashMap<>();

			addQueryParameters(map, url.getQuery());

			map.put("function.location",
				String.join(":", url.getProtocol(), url.getPath()));

			addOrReplace(environment.getPropertySources(), map);
		}
	}

	private void addQueryParameters(Map<String, Object> map, String query) {

		if (StringUtils.hasText(query)) {

			Map<String, String> params = new HashMap<>();
			Stream.of(query.split("&"))
				.forEach(s -> {
					String[] pair = s.split("=");
					if (pair.length == 2) {
						params.put(pair[0], pair[1]);
					}
				});
			if (params.containsKey("main")) {
				map.put("function.main", params.get("main"));
			}

			if (params.containsKey("handler")) {
				map.put("function.bean", params.get("handler"));
			}
		}
	}

	private void addOrReplace(MutablePropertySources propertySources,
		Map<String, Object> map) {
		MapPropertySource target = null;
		if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
			PropertySource<?> source = propertySources.get(PROPERTY_SOURCE_NAME);
			if (source instanceof MapPropertySource) {
				target = (MapPropertySource) source;
				for (String key : map.keySet()) {
					if (!target.containsProperty(key)) {
						target.getSource().put(key, map.get(key));
					}
				}
			}
		}
		if (target == null) {
			target = new MapPropertySource(PROPERTY_SOURCE_NAME, map);
		}
		if (!propertySources.contains(PROPERTY_SOURCE_NAME)) {
			propertySources.addLast(target);
		}
	}
}
