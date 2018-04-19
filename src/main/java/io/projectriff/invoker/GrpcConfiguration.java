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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.google.gson.Gson;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
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
@ConditionalOnProperty(prefix = "grpc", name = "enabled", matchIfMissing = true)
public class GrpcConfiguration {

	private static final Log logger = LogFactory.getLog(GrpcConfiguration.class);
	private Server server;
	/**
	 * The port to listen on for gRPC connections.
	 */
	private int port = 10382;
	/**
	 * Flag to indicate that the application should shutdown after a successful gRPC call.
	 */
	private boolean exitOnComplete;
	/**
	 * Flag to enable or disable the gRPC server.
	 */
	private boolean enabled = true;

	@Autowired
	private Gson mapper;
	@Autowired
	private FunctionProperties functions;
	@Autowired
	private FunctionInspector inspector;
	@Autowired
	private FunctionCatalog catalog;
	@Autowired
	private ConfigurableApplicationContext context;

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		port = port > 0 ? port : SocketUtils.findAvailableTcpPort();
		this.port = port;
	}

	public boolean isExitOnComplete() {
		return exitOnComplete;
	}

	public void setExitOnComplete(boolean exitOnComplete) {
		this.exitOnComplete = exitOnComplete;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** Start serving requests. */
	@EventListener(ContextRefreshedEvent.class)
	public void start() {
		try {
			Object function = catalog.lookup(Function.class, functions.getName());
			if (function == null) {
				function = catalog.lookup(Consumer.class, functions.getName());
				if (function == null) {
					function = catalog.lookup(Supplier.class, functions.getName());
					if (function == null) {
						throw new IllegalStateException(
								"No such function: " + functions.getName());
					}
				}
			}
			this.server = ServerBuilder.forPort(this.port)
					.addService(
							new JavaFunctionInvokerServer(
									function(function,
											inspector.getRegistration(function)
													.getType()),
									this::maybeClose, this.mapper,
									inspector.getInputType(function),
									inspector.getOutputType(function),
									inspector.isMessage(function)))
					.build();
			this.server.start();
		}
		catch (IOException e) {
			throw new IllegalStateException(String
					.format("gRPC server failed to start listening on port %d", port), e);
		}
		logger.info("Server started, listening on " + port);
		awaitTermination();
	}

	public void awaitTermination() {
		if (server != null) {
			Thread thread = new Thread(() -> {
				try {
					logger.info("Waiting for server to terminate");
					server.awaitTermination();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			thread.setName("grpcServerWait");
			thread.setDaemon(false);
			thread.start();
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

	/**
	 * If a Supplier or Consumer has been registered (as opposed to a Function), convert
	 * it to a Function in such a way that it will discard inputs or outputs, depending on
	 * its type.
	 */
	private Function<Flux<?>, Flux<?>> function(Object result, FunctionType type) {
		if (result instanceof Supplier) {
			if (type == null) {
				type = FunctionType.of(result.getClass());
			}
			// If we don't supply a Flux already, let's make sure we get one
			if (!type.isWrapper() && !(result instanceof FluxSupplier)) {
				result = new FluxSupplier<>((Supplier<?>) result);
			}
			// Adapt the supplier of Flux to be a Function
			@SuppressWarnings("unchecked")
			Supplier<Publisher<?>> supplier = (Supplier<Publisher<?>>) result;
			result = new SupplierAdapter(supplier);
		}
		if (result instanceof Consumer) {
			if (type == null) {
				type = FunctionType.of(result.getClass());
			}
			// If we don't consume a Flux already, let's make sure we can
			if (!type.isWrapper()) {
				FluxConsumer<?> function = null;
				if (result instanceof FluxConsumer) {
					function = (FluxConsumer<?>) result;
				}
				else {
					function = new FluxConsumer<>((Consumer<?>) result);
				}
				result = new FluxConsumerAdapter(function);
			}
			else {
				// Only get to here if user supplied a Consumer<Flux<?>>
				result = new ConsumerAdapter((Consumer<?>) result);
			}
		}
		@SuppressWarnings("unchecked")
		Function<Flux<?>, Flux<?>> output = (Function<Flux<?>, Flux<?>>) result;
		return output;
	}

	private void maybeClose() {
		if (this.exitOnComplete) {
			ApplicationContext context = this.context;
			while (context instanceof ConfigurableApplicationContext) {
				ConfigurableApplicationContext closeable = (ConfigurableApplicationContext) context;
				if (closeable.isRunning()) {
					closeable.close();
				}
				context = context.getParent();
			}
		}
	}

	private static final class ConsumerAdapter
			implements Function<Flux<Object>, Flux<Object>> {

		private Consumer<Flux<Object>> result;

		@SuppressWarnings("unchecked")
		public ConsumerAdapter(Consumer<?> result) {
			this.result = (Consumer<Flux<Object>>) result;
		}

		@Override
		public Flux<Object> apply(Flux<Object> t) {
			Flux<Object> input = t.share();
			result.accept(input);
			return Flux.from(input.then());
		}

	}

	private static final class SupplierAdapter
			implements Function<Flux<Object>, Flux<Object>> {

		private Supplier<Publisher<?>> result;

		public SupplierAdapter(Supplier<Publisher<?>> result) {
			this.result = result;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Flux<Object> apply(Flux<Object> input) {
			return (Flux<Object>) Flux.from(result.get());
		}

	}

	private static final class FluxConsumerAdapter
			implements Function<Flux<Object>, Flux<Object>> {

		private FluxConsumer<Object> result;

		@SuppressWarnings("unchecked")
		public FluxConsumerAdapter(FluxConsumer<?> result) {
			this.result = (FluxConsumer<Object>) result;
		}

		@Override
		public Flux<Object> apply(Flux<Object> t) {
			return Flux.from(result.apply(t).then());
		}

	}

}
