package io.projectriff.invoker.client;

import io.grpc.Channel;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuple6;
import reactor.util.function.Tuple7;
import reactor.util.function.Tuple8;

import java.util.function.Supplier;

public class SupplierClient<O>  {

    public static <O1> Supplier<Flux<O1>> of(Channel channel, Class<O1> outputType) {
        FunctionClient<?, Flux<O1>> functionClient = FunctionClient.of(channel, outputType);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2> Supplier<Tuple2<Flux<O1>, Flux<O2>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2) {
        FunctionClient<?, Tuple2<Flux<O1>, Flux<O2>>> functionClient = FunctionClient.of(channel, outputType1, outputType2);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2, O3> Supplier<Tuple3<Flux<O1>, Flux<O2>, Flux<O3>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3) {
        FunctionClient<?, Tuple3<Flux<O1>, Flux<O2>, Flux<O3>>> functionClient = FunctionClient.of(channel, outputType1, outputType2, outputType3);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2, O3, O4> Supplier<Tuple4<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4) {
        FunctionClient<?, Tuple4<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>>> functionClient = FunctionClient.of(channel, outputType1, outputType2, outputType3, outputType4);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2, O3, O4, O5> Supplier<Tuple5<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5) {
        FunctionClient<?, Tuple5<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>>> functionClient = FunctionClient.of(channel, outputType1, outputType2, outputType3, outputType4, outputType5);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2, O3, O4, O5, O6> Supplier<Tuple6<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5, Class<O6> outputType6) {
        FunctionClient<?, Tuple6<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>>> functionClient = FunctionClient.of(channel, outputType1, outputType2, outputType3, outputType4, outputType5, outputType6);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2, O3, O4, O5, O6, O7> Supplier<Tuple7<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>, Flux<O7>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5, Class<O6> outputType6, Class<O7> outputType7) {
        FunctionClient<?, Tuple7<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>, Flux<O7>>> functionClient = FunctionClient.of(channel, outputType1, outputType2, outputType3, outputType4, outputType5, outputType6, outputType7);
        return () -> functionClient.apply(null);
    }

    public static <O1, O2, O3, O4, O5, O6, O7, O8> Supplier<Tuple8<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>, Flux<O7>, Flux<O8>>> of(Channel channel, Class<O1> outputType1, Class<O2> outputType2, Class<O3> outputType3, Class<O4> outputType4, Class<O5> outputType5, Class<O6> outputType6, Class<O7> outputType7, Class<O8> outputType8) {
        FunctionClient<?, Tuple8<Flux<O1>, Flux<O2>, Flux<O3>, Flux<O4>, Flux<O5>, Flux<O6>, Flux<O7>, Flux<O8>>> functionClient = FunctionClient.of(channel, outputType1, outputType2, outputType3, outputType4, outputType5, outputType6, outputType7, outputType8);
        return () -> functionClient.apply(null);
    }
}
