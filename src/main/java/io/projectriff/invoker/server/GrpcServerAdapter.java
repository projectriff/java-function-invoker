package io.projectriff.invoker.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import io.projectriff.invoker.rpc.InputSignal;
import io.projectriff.invoker.rpc.OutputFrame;
import io.projectriff.invoker.rpc.OutputSignal;
import io.projectriff.invoker.rpc.ReactorRiffGrpc;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Signal;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Reactive gRPC adapter for riff.
 *
 * @author Eric Bottard
 */
public class GrpcServerAdapter extends ReactorRiffGrpc.RiffImplBase {

    private final FunctionCatalog functionCatalog;

    private final FunctionInspector functionInspector;

    private final String functionName;

    public GrpcServerAdapter(FunctionCatalog functionCatalog, FunctionInspector functionInspector, String functionName) {
        this.functionCatalog = functionCatalog;
        this.functionInspector = functionInspector;
        this.functionName = functionName;
    }

    @Override
    public Flux<OutputSignal> invoke(Flux<InputSignal> request) {
        return request
                .switchOnFirst((first, stream) -> {
                    if (!first.hasValue() || !first.get().hasStart()) {
                        return Flux.error(new RuntimeException("Expected first frame to be of type Start"));
                    }

                    String[] accept = getExpectedOutputContentTypes(first);

                    Function<Object, Object> userFn = functionCatalog.lookup(functionName, accept);

                    return stream
                            .skip(1L)
                            .map(this::toSpringMessage)
                            .transform(invoker(userFn))
                            .map(this::fromSpringMessage)
                            .doOnError(Throwable::printStackTrace);
                });
    }

    private String[] getExpectedOutputContentTypes(Signal<? extends InputSignal> first) {
        InputSignal firstSignal = first.get();
        ProtocolStringList expectedContentTypesList = firstSignal.getStart().getExpectedContentTypesList();
        return expectedContentTypesList.toArray(String[]::new);
    }

    private Tuple2<Integer, Message<byte[]>> toSpringMessage(InputSignal in) {
        int argIndex = in.getData().getArgIndex();
        String contentType = in.getData().getContentType();
        Message<byte[]> message = MessageBuilder
                .withPayload(in.getData().getPayload().toByteArray())
                .setHeader(MessageHeaders.CONTENT_TYPE, contentType)
                .copyHeadersIfAbsent(in.getData().getHeadersMap())
                .build();
        return Tuples.of(argIndex, message);
    }

    private OutputSignal fromSpringMessage(Tuple2<Integer, Message<byte[]>> out) {
        int resultIndex = out.getT1();
        MessageHeaders headers = out.getT2().getHeaders();
        MimeType contentType = headers.get(MessageHeaders.CONTENT_TYPE, MimeType.class);
        OutputFrame.Builder builderForOutputFrame = OutputFrame.newBuilder()
                .setContentType(contentType.toString())
                .setResultIndex(resultIndex)
                .setPayload(ByteString.copyFrom(out.getT2().getPayload()));

        headers.entrySet().stream().filter(e -> !e.getKey().equals(MessageHeaders.CONTENT_TYPE) && e.getValue() instanceof String)
                .forEach(e -> builderForOutputFrame.putHeaders(e.getKey(), (String) e.getValue()));
        return OutputSignal.newBuilder()
                .setData(builderForOutputFrame)
                .build();
    }

    private Function<Flux<Tuple2<Integer, Message<byte[]>>>, Flux<Tuple2<Integer, Message<byte[]>>>> invoker(Function<Object, Object> springCloudFunction) {

        Type functionType = FunctionTypeUtils.getFunctionType(springCloudFunction, this.functionInspector);
        int arity = FunctionTypeUtils.getInputCount(functionType);

        Tuple2<Integer, Message<byte[]>>[] startTuples = new Tuple2[arity];
        for (int i = 0; i < startTuples.length; i++) {
            startTuples[i] = Tuples.of(i, new GenericMessage<>(new byte[0]));
        }


        return
                // stick dummy messages in front to force the creation of each arg-index group
                flux -> flux.startWith(startTuples)
                        // group by arg index (ie de-mux)
                        .groupBy(Tuple2::getT1, Tuple2::getT2)
                        // chop the outer flux. We know there will ever be exactly that many groups
                        .take(startTuples.length)
                        // collect in order
                        .collectSortedList(Comparator.comparingInt(GroupedFlux::key))

                        .flatMapMany(groupList -> {
                            // skip(1) below drops the dummy messages which were introduced above
                            Object[] args = groupList.stream().map(g -> g.skip(1)).toArray(Object[]::new);
                            Object tuple = asTupleOrSingleArg(args);
                            // apply the function
                            Object result = springCloudFunction.apply(tuple);

                            Flux<Message<byte[]>>[] bareOutputs = promoteToArray(result);
                            // finally, merge all fluxes as Tuple2s with the output index set
                            Flux<Tuple2<Integer, Message<byte[]>>>[] withOutputIndices = new Flux[bareOutputs.length];
                            for (int i = 0; i < bareOutputs.length; i++) {
                                int j = i;
                                withOutputIndices[i] = bareOutputs[i].map(msg -> Tuples.of(j, msg));
                            }
                            return Flux.merge(withOutputIndices);

                        })
                ;
    }

    private Flux<Message<byte[]>>[] promoteToArray(Object result) {
        if (result instanceof Tuple2) {
            Object[] objects = ((Tuple2) result).toArray();
            Flux<Message<byte[]>>[] fluxArray = new Flux[objects.length];
            for (int i = 0; i < objects.length; i++) {
                fluxArray[i] = (Flux<Message<byte[]>>) objects[i];
            }
            return fluxArray;
        } else {
            Flux<Message<byte[]>> item = (Flux<Message<byte[]>>) result;
            return new Flux[]{item};
        }
    }

    private Object asTupleOrSingleArg(Object[] args) {
        switch (args.length) {
            case 0:
                return null;
            case 1:
                return args[0];
            default:
                return Tuples.fromArray(args);
        }
    }

}
