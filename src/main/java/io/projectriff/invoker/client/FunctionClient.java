package io.projectriff.invoker.client;

import io.grpc.Channel;
import io.projectriff.invoker.rpc.ReactorRiffGrpc;

import java.util.function.Function;

public class FunctionClient<I, O> implements Function<I, O> {

    private final ReactorRiffGrpc.ReactorRiffStub riffStub;

    public FunctionClient(Channel channel) {
        this.riffStub = ReactorRiffGrpc.newReactorStub(channel);
    }

    @Override
    public O apply(I i) {
        return null;
    }
}
