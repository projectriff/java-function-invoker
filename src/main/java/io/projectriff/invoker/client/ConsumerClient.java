package io.projectriff.invoker.client;

import io.grpc.Channel;

import java.util.function.Consumer;

public class ConsumerClient<O> {

    public static <I> Consumer<I> of(Channel channel) {
        FunctionClient<I, ?> functionClient = FunctionClient.of(channel, Object.class);
        return functionClient::apply;
    }
}
