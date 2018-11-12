/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 */
public class FunctionEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static Pattern uriPattern = Pattern.compile("(.+)\\?.*handler=([^&]+)&?(.*)");

	private static final String PROPERTY_SOURCE_NAME = "defaultProperties";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
			SpringApplication application) {
		String uri = environment.getProperty("function.uri");
		if (StringUtils.hasText(uri)) {
			Map<String, Object> map = new HashMap<String, Object>();
			Matcher m = uriPattern.matcher(uri);
			boolean matches = m.matches();

			String jarLocation = matches ? m.group(1) : uri;
			String className = matches ? m.group(2) : null;
			String rest = matches ? m.group(3) : null;
			if (rest != null && rest.startsWith("main=")) {
				map.put("function.main", rest.substring("main=".length()));
			}

			map.put("function.location", jarLocation);
			if (className != null && className.trim().length()>0) {
				map.put("function.bean", className);
			}
			addOrReplace(environment.getPropertySources(), map);
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
