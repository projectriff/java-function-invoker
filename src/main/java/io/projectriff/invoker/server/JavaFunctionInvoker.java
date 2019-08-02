package io.projectriff.invoker.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.deployer.EnableFunctionDeployer;
import org.springframework.cloud.function.deployer.FunctionProperties;
import org.springframework.context.annotation.Bean;

/**
 * This class sets up all the necessary infrastructure for exposing a function over riff gRPC protocol as well as plain http.
 *
 * <p>Heavy lifting is done via Spring Cloud Function and the function deployer and then the located function
 * is adapted to reactive-grpc server and http handler.</p>
 *
 * @author Eric Bottard
 */
@SpringBootApplication
@EnableFunctionDeployer
public class JavaFunctionInvoker {

    /*
     * Exposes an object capable of running a gRPC server with the function.
     * Startup is done in an init methodHandle to work around late initialization needs of the function deployer.
     */
    @Bean(initMethod = "run", destroyMethod = "close")
    public GrpcRunner grpcRunner(FunctionCatalog functionCatalog, FunctionInspector functionInspector, FunctionProperties functionProperties) {
        return new GrpcRunner(functionCatalog, functionInspector, functionProperties.getName());
    }

    private static class GrpcRunner {

        private Server server;

        public GrpcRunner(FunctionCatalog functionCatalog, FunctionInspector functionInspector, String functionName) {
            GrpcServerAdapter adapter = new GrpcServerAdapter(functionCatalog, functionInspector, functionName);
            server = ServerBuilder.forPort(8081).addService(adapter).build();
        }

        public void run() throws Exception {
            server.start();
        }

        public void close() {
            server.shutdown();
        }

    }

    /*
    @Bean(initMethod = "run", destroyMethod = "close")
    public HttpRunner httpRunner(FunctionCatalog functionCatalog, FunctionProperties functionProperties) {
        return new HttpRunner(functionCatalog, functionProperties.getName());
    }

     */

    private static class HttpRunner {

        private static final int PORT = 8089;
        private final ServerBootstrap bootstrap;
        private Channel channel;

        public HttpRunner(FunctionCatalog functionCatalog, String functionName) {
            // Configure the server.
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup();
            this.bootstrap = new ServerBootstrap();
            this.bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new CustomChannelInitializer(new HttpServerHandler(functionCatalog, functionName)));
        }

        public void run() throws Exception {
            this.channel = bootstrap.bind(PORT).sync().channel();
        }

        public void close() throws InterruptedException {
            this.channel.close().sync();
        }
    }

    private static class CustomChannelInitializer extends ChannelInitializer<SocketChannel> {
        private final HttpServerHandler methodHandler;

        public CustomChannelInitializer(HttpServerHandler methodHandler) {
            this.methodHandler = methodHandler;
        }

        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
            ChannelPipeline pipeline = socketChannel.pipeline();
            pipeline.addLast(new HttpRequestDecoder());
            pipeline.addLast(new HttpResponseEncoder());
            pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
            pipeline.addLast(methodHandler);
        }
    }

}
