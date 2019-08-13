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
import reactor.util.function.*;

import java.util.*;
import java.util.function.Function;

/**
 * FunctionClient is a client-side helper class to invoke riff streaming function over gRPC.
 *
 * <p>It performs client-side serialization/deserializaton and mux/demux of parameters and return values.</p>
 * <p>By default, only a converter for {@code application/json} is set up, but users can override this via {@link #setMessageConverters(AbstractMessageConverter...)}.</p>
 *
 * @param <I> The input type of the function, typycally {@code Flux<T>} or {@code TupleN<Flux<T>, Flux<U>, ...>}
 * @param <>  The output type of the function, typycally {@code Flux<R>} or {@code TupleM<Flux<R>, Flux<S>, ...>}
 * @author Eric Bottard
 */
public class FunctionClient<I, O> implements Function<I, O> {

    private final ReactorRiffGrpc.ReactorRiffStub riffStub;

    private String supportedOutputTypes;

    private final Class[] outputTypes;
    private CompositeMessageConverter messageConverter;

    private FunctionClient(Channel channel, Class... outputTypes) {
        Hooks.onOperatorDebug();

        this.riffStub = ReactorRiffGrpc.newReactorStub(channel);
        this.outputTypes = outputTypes;
        setMessageConverters(new MappingJackson2MessageConverter());
    }

    public static <I, O1> FunctionClient<I, Flux<O1>> of(Channel channel, Class<O1> outputType) {
        return new FunctionClient<>(channel, outputType);
    }

    public static <I, O1, O2> FunctionClient<I, Tuple2<Flux<O1>, Flux<O2>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2) {
        return new FunctionClient<>(channel, outputType1, outputType2);
    }

    public static <I, O1, O2, O3> FunctionClient<I, Tuple3<Flux<O1>, Flux<O2>, Flux<O3>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3) {
        return new FunctionClient<>(channel, outputType1, outputType2, outputType3);
    }

    public static <I, O1, O2, O3, O4> FunctionClient<I, Tuple4<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4) {
        return new FunctionClient<>(channel, outputType1, outputType2, outputType3, outputType4);
    }

    public static <I, O1, O2, O3, O4, O5> FunctionClient<I, Tuple5<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5) {
        return new FunctionClient<>(channel, outputType1, outputType2, outputType3, outputType4, outputType5);
    }

    public static <I, O1, O2, O3, O4, O5, O6> FunctionClient<I, Tuple6<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5, Class<O6> outputType6) {
        return new FunctionClient<>(channel, outputType1, outputType2, outputType3, outputType4, outputType5, outputType6);
    }

    public static <I, O1, O2, O3, O4, O5, O6, O7> FunctionClient<I, Tuple7<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>, Flux<O7>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5, Class<O6> outputType6, Class<O7> outputType7) {
        return new FunctionClient<>(channel, outputType1, outputType2, outputType3, outputType4, outputType5, outputType6, outputType7);
    }

    public static <I, O1, O2, O3, O4, O5, O6, O7, O8> FunctionClient<I, Tuple8<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>, Flux<O7>, Flux<O8>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5, Class<O6> outputType6, Class<O7> outputType7, Class<O8> outputType8) {
        return new FunctionClient<>(channel, outputType1, outputType2, outputType3, outputType4, outputType5, outputType6, outputType7, outputType8);
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

        Flux<InputSignal> allInputSignals = mergeWithArgIndices(input);

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

    private Flux<InputSignal> mergeWithArgIndices(I input) {
        Flux<InputSignal> allInputSignals = Flux.empty();
        if (input instanceof Flux) {
            Flux<?> t1 = (Flux<?>) input;
            allInputSignals = allInputSignals.mergeWith(t1.map(v -> toRiffSignal(v, 0)));
        } else {
            if (input instanceof Tuple2) {
                Flux<?> t1 = (Flux<?>) ((Tuple2) input).getT1();
                allInputSignals = allInputSignals.mergeWith(t1.map(v -> toRiffSignal(v, 0)));
                Flux<?> t2 = (Flux<?>) ((Tuple2) input).getT2();
                allInputSignals = allInputSignals.mergeWith(t2.map(v -> toRiffSignal(v, 1)));
            }
            if (input instanceof Tuple3) {
                Flux<?> t3 = (Flux<?>) ((Tuple3) input).getT3();
                allInputSignals = allInputSignals.mergeWith(t3.map(v -> toRiffSignal(v, 2)));
            }
            if (input instanceof Tuple4) {
                Flux<?> t4 = (Flux<?>) ((Tuple4) input).getT4();
                allInputSignals = allInputSignals.mergeWith(t4.map(v -> toRiffSignal(v, 3)));
            }
            if (input instanceof Tuple5) {
                Flux<?> t5 = (Flux<?>) ((Tuple5) input).getT5();
                allInputSignals = allInputSignals.mergeWith(t5.map(v -> toRiffSignal(v, 4)));
            }
            if (input instanceof Tuple6) {
                Flux<?> t6 = (Flux<?>) ((Tuple6) input).getT6();
                allInputSignals = allInputSignals.mergeWith(t6.map(v -> toRiffSignal(v, 5)));
            }
            if (input instanceof Tuple7) {
                Flux<?> t7 = (Flux<?>) ((Tuple7) input).getT7();
                allInputSignals = allInputSignals.mergeWith(t7.map(v -> toRiffSignal(v, 6)));
            }
            if (input instanceof Tuple8) {
                Flux<?> t8 = (Flux<?>) ((Tuple8) input).getT8();
                allInputSignals = allInputSignals.mergeWith(t8.map(v -> toRiffSignal(v, 7)));
            }
        }
        return allInputSignals;
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
        InputFrame.Builder frame = InputFrame.newBuilder()
                .setArgIndex(index)
                .setContentType(message.getHeaders().get(MessageHeaders.CONTENT_TYPE).toString())
                .setPayload(ByteString.copyFrom((byte[]) message.getPayload()));
        message.getHeaders().forEach((h, v) -> frame.putHeaders(h, v.toString()));
        InputSignal signal = InputSignal.newBuilder()
                .setData(frame.build())
                .build();
        return signal;
    }
}
