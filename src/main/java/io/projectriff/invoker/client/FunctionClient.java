package io.projectriff.invoker.client;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.projectriff.invoker.rpc.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Hooks;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.function.Function;

public class FunctionClient<I, O> implements Function<I, O> {

    private final ReactorRiffGrpc.ReactorRiffStub riffStub;

    private String supportedOutputTypes;

    private final Class[] outputTypes;
    private CompositeMessageConverter messageConverter;

    public FunctionClient(Channel channel, Class... outputTypes) {
        Hooks.onOperatorDebug();

        this.riffStub = ReactorRiffGrpc.newReactorStub(channel);
        this.outputTypes = outputTypes;
        setMessageConverters(new MappingJackson2MessageConverter());
    }

    public void setMessageConverters(AbstractMessageConverter... converters) {
        this.messageConverter = new CompositeMessageConverter(Arrays.asList(converters));

        Set<MimeType> mimeTypes = new LinkedHashSet<>();
        for (AbstractMessageConverter converter : converters) {
            mimeTypes.addAll(converter.getSupportedMimeTypes());
        }
        List<MimeType> sorted = new ArrayList<>(mimeTypes);
        MimeTypeUtils.sortBySpecificity(sorted);
        this.supportedOutputTypes = MimeTypeUtils.toString(sorted);

    }

    @Override
    public O apply(I input) {

        int n = this.outputTypes.length;

        InputSignal start = InputSignal.newBuilder()
                .setStart(StartFrame.newBuilder()
                        .addAllExpectedContentTypes(Collections.nCopies(n, this.supportedOutputTypes))
                        .build())
                .build();

        Flux<InputSignal> allInputSignals = Flux.empty();
        if (input instanceof Tuple2) {
            Flux<?> t1 = (Flux<?>) ((Tuple2) input).getT1();
            allInputSignals = allInputSignals.mergeWith(t1.map(v -> toRiffSignal(v, 0)));
            Flux<?> t2 = (Flux<?>) ((Tuple2) input).getT2();
            allInputSignals = allInputSignals.mergeWith(t2.map(v -> toRiffSignal(v, 1)));
        }

        Flux<OutputSignal> response = riffStub.invoke(Flux.concat(
                Flux.just(start),
                allInputSignals
        ));

        OutputSignal[] usedToForceGroups = new OutputSignal[n];
        for (int i = 0; i < n; i++) {
            usedToForceGroups[i] = OutputSignal.newBuilder()
                    .setData(OutputFrame.newBuilder()
                            .setResultIndex(i)
                            .build())
                    .build();
        }

        Flux[] fluxArray = response
                .startWith(Flux.fromArray(usedToForceGroups))
                .groupBy(sig -> sig.getData().getResultIndex())
                .take(n)
                .sort(Comparator.comparingInt(GroupedFlux::key))
                .map(g -> g.skip(1)/*drop init frames*/.map(s -> convertFromSignal(s, this.outputTypes[g.key()])))
                .collectList()
                .block()
                .toArray(Flux[]::new);
        return (O) ((fluxArray.length >= 2) ? Tuples.fromArray(fluxArray) : fluxArray[0]);
    }

    private Object convertFromSignal(OutputSignal s, Class type) {
        Message<byte[]> m = MessageBuilder
                .withPayload(s.getData().getPayload().toByteArray())
                .setHeader(MessageHeaders.CONTENT_TYPE, s.getData().getContentType())
                .build();
        Object o = this.messageConverter.fromMessage(m, type);
        return o;
    }

    private InputSignal toRiffSignal(Object value, int index) {
        Message<?> message = this.messageConverter.toMessage(value, null);
        if (message == null) {
            throw new MessageConversionException("Could not find a suitable converter for value of type " + value.getClass());
        }
        InputSignal signal = InputSignal.newBuilder()
                .setData(InputFrame.newBuilder()
                        .setArgIndex(index)
                        .setContentType(message.getHeaders().get(MessageHeaders.CONTENT_TYPE).toString())
                        .setPayload(ByteString.copyFrom((byte[]) message.getPayload()))
                        // TODO: other headers
                        .build())
                .build();
        return signal;
    }
}
