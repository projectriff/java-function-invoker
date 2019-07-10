package io.projectriff.invoker.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import io.projectriff.invoker.rpc.InputSignal;
import io.projectriff.invoker.rpc.OutputFrame;
import io.projectriff.invoker.rpc.OutputSignal;
import io.projectriff.invoker.rpc.ReactorRiffGrpc;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Signal;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GrpcServerAdapter extends ReactorRiffGrpc.RiffImplBase {

    private final FunctionCatalog functionCatalog;

    private final String functionName;

    public GrpcServerAdapter(FunctionCatalog functionCatalog, String functionName) {
        this.functionCatalog = functionCatalog;
        this.functionName = functionName;
    }

    @Override
    public Flux<OutputSignal> invoke(Flux<InputSignal> request) {
        return request
                .switchOnFirst((first, stream) -> {
                    if (!first.hasValue() || !first.get().hasStart()) {
                        return Flux.error(new RuntimeException("Expected first frame to be of type Start"));
                    }
                    List<List<MimeType>> accept = getExpectedOutputContentTypes(first);
                    MimeType[] downgraded = accept.stream().map(list -> list.get(0)).toArray(MimeType[]::new);

                    System.out.println("Expecting " + Arrays.asList(downgraded));

                    Function<Object, Object> userFn = functionCatalog.lookup(functionName, downgraded);
                    System.out.println("userFn = " + userFn + "\nnames = " + functionCatalog.getNames(Function.class) + "\n");

                    return stream
                            .skip(1L)
                            .map(this::toSpringMessage)
                            .transform(invoker(userFn))
                            .map(this::fromSpringMessage)
                            .doOnError(Throwable::printStackTrace);
                });
    }

    private List<List<MimeType>> getExpectedOutputContentTypes(Signal<? extends InputSignal> first) {
        InputSignal firstSignal = first.get();
        ProtocolStringList expectedContentTypesList = firstSignal.getStart().getExpectedContentTypesList();
        return expectedContentTypesList.stream().map(MimeTypeUtils::parseMimeTypes).collect(Collectors.toList());
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
        int arity = 2;
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
                            Object[] args = groupList.stream().map(g -> g.skip(1)).toArray(Object[]::new);
                            Object tuple = asTupleOrSingleArg(args);

                            Object result = springCloudFunction.apply(tuple);

                            Flux<Message<byte[]>>[] bareOutputs = promoteToArray(result);
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
            return (Flux<Message<byte[]>>[]) result;
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
