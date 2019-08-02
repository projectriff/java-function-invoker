package io.projectriff.invoker.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@ChannelHandler.Sharable
class HttpServerHandler extends SimpleChannelInboundHandler<Object> {

    private final FunctionCatalog functionCatalog;

    private final String functionName;

    HttpServerHandler(FunctionCatalog functionCatalog, String functionName) {
        this.functionName = functionName;
        this.functionCatalog = functionCatalog;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext context, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            return;
        }
        FullHttpRequest request = (FullHttpRequest) msg;
        if (!request.method().equals(HttpMethod.POST)) {
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.buffer());
            context.write(response);
            context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        ByteBuf content = request.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        try {

            io.netty.handler.codec.http.HttpHeaders headers = request.headers();
            String accept = headers.getAsString(HttpHeaderNames.ACCEPT);
            if (!StringUtils.hasText(accept)) {
                accept = "application/octet-stream";
            }
            Function<Flux<Message<byte[]>>, Flux<Message<byte[]>>> userFn = functionCatalog.lookup(this.functionName, accept);

            MessageHeaderAccessor accessor = new MessageHeaderAccessor();
            headers.forEach(e -> accessor.setHeader(e.getKey(), e.getValue()));
            // Spring Message expects 'contentType' as the header name
            accessor.setContentType(MimeTypeUtils.parseMimeType(headers.getAsString(HttpHeaderNames.CONTENT_TYPE)));
            Message<byte[]> inputMessage = MessageBuilder.createMessage(bytes, accessor.getMessageHeaders());

            Flux<Message<byte[]>> result = userFn.apply(Flux.just(inputMessage));
            Message<byte[]> outputMessage = result.blockFirst();

            ByteBuf out = Unpooled.buffer();

            ByteBufOutputStream os = new ByteBufOutputStream(out);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    out);
            os.write(outputMessage.getPayload());
            outputMessage.getHeaders().forEach((k, vs) -> response.headers().add(k, vs));

            context.write(response);
            context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}