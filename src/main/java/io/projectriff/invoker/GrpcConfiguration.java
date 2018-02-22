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

import java.io.IOException;
import java.util.function.Function;

import javax.annotation.PreDestroy;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.google.gson.Gson;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.SocketUtils;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConfigurationProperties("grpc")
public class GrpcConfiguration {

	private static final Log logger = LogFactory.getLog(GrpcConfiguration.class);
	private Server server;
	private int port = 10382;

	@Autowired
	private Gson mapper;
	@Autowired
	private FunctionProperties functions;
	@Autowired
	private FunctionInspector inspector;
	@Autowired
	private FunctionCatalog catalog;

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		port = port > 0 ? port : SocketUtils.findAvailableTcpPort();
		this.port = port;
	}

	/** Start serving requests. */
	@EventListener(ContextRefreshedEvent.class)
	public void start() throws IOException {
		try {
			Function<Flux<?>, Flux<?>> function = catalog
					.lookupFunction(functions.getFunctionName());
			this.server = ServerBuilder.forPort(this.port)
					.addService(new JavaFunctionInvokerServer(function, this.mapper,
							inspector.getInputType(function),
							inspector.getOutputType(function), inspector.isMessage(function)))
					.build();
			this.server.start();
		} catch (IOException e) {
			throw new IOException(String.format("gRPC server failed to start listening on port %d", port), e);
		}
		logger.info("Server started, listening on " + port);
	}

	public void awaitTermination() {
		if (server != null) {
			try {
				server.awaitTermination();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/** Stop serving requests and shutdown resources. */
	@PreDestroy
	public void stop() {
		if (this.server != null) {
			logger.info("Server shutting down on " + port);
			this.server.shutdown();
		}
		// This shouldn't be necessary, and doesn't seem to help anyway. How to stop the
		// class loader from being destroyed before the server has stopped?
		awaitTermination();
	}

}
