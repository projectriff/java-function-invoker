Testing locally:

```
$ ./mvnw install dockerfile:build
$ docker run -ti -p 8080:8080 -v `pwd`/target/test-classes:/classes projectriff/java-function-invoker:0.0.4-SNAPSHOT --function.uri=file:classes?handler=io.projectriff.functions.Doubler
```

Then

```
$ curl -v localhost:8080 -H "Content-Type: text/plain" -d 5
10
```
