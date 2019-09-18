# Java Function Invoker [![Build Status](https://travis-ci.com/projectriff/java-function-invoker.svg?branch=master)](https://travis-ci.com/projectriff/java-function-invoker)

## What It Does

The "Java Function Invoker" lets you concentrate on writing your business logic as a Java function while the invoker 
takes care of the rest that is needed to run your functions in a Kubernetes cluster with riff installed.
The invoker is a [Spring Boot](https://projects.spring.io/spring-boot) application that will locate your function in 
the JAR file you provide based on some configuration settings.
It will then expose the function over gRPC using riff's [streaming protocol](src/main/proto/riff-rpc.proto).
When used in a function service like [riff](https://projectriff.io/), the invoker boot application is provided by 
the platform when functions are built and basic request/reply http support is added via 
[the streaming/http adapter](http://github.com/projectriff/streaming-http-adapter-buildpack).

## How To Use It

### Function source

You need to configure a Maven or Gradle project for your function.
If you use Spring Boot then we recommend using [Spring Initializr](https://start.spring.io/) to bootstrap your project.
If you are not using Spring Boot for your function code then you need to create your own build configuration.
There are no required dependencies from the Java Function Invoker, it only requires that your function implements the `java.util.function.Function` interface.

The [Spring Cloud Function](https://cloud.spring.io/spring-cloud-function/) project provides support for writing functions as part of a Spring Boot app.

Example of a plain Java function:

```java
package functions;

import java.util.function.Function;

public class Upper implements Function<String, String> {

    public String apply(String name) {
        return name.toUpperCase();
    }
}
```

Example of a Spring Boot app with a function bean:

```java
package com.example.uppercase;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UppercaseApplication {

	@Bean
	Function<String, String> uppercase() {
		return s -> s.toUpperCase();
	}

	public static void main(String[] args) {
		SpringApplication.run(UppercaseApplication.class, args);
	}
}
```

#### Function detection

Spring Cloud Function will attempt to detect the function from the function source.
If you have a single function declared with `@Bean` in a Spring Boot app then that is the function that will be used.
If you have multiple functions in the source then you have to specify which one you want using a bean name (see the next section).

If you have a Plain Java class with a function then you can provide a `Function-Class` entry in the JAR file manifest 
to indicate which function class to use or specify its class explicitly.
Here is an example of using a plug-in for Maven to add this information to the manifest:

```xml
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>3.0.2</version>
        <configuration>
          <archive>
            <manifestEntries>
              <Function-Class>functions.Greeter</Function-Class>
            </manifestEntries>
          </archive>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

#### Function handler

If your function can't be automatically detected then you need to provide a handler specification.
The simplest form of the handler is a bean name or a class name that can be instantiated (with a default constructor).
More complex creation scenarios can be handled by giving the handler via configuration properties:

* `spring.cloud.function.function-location` the file path of a jar file containing the function class, Boot uber-jar or vanilla jar,
* `spring.cloud.function.function-class` is a class name,
* `spring.cloud.function.definition` is a bean name,


#### Function samples

We have sample application for 
[plain Java function](src/test/function-sources/hundred-divider) 
and for [Spring Boot app with a function bean](src/test/function-sources/repeater-as-bean).

### Cloud Native Buildpacks

[Buildpacks](https://buildpacks.io/) provide a higher-level abstraction for building apps compared to Dockerfiles.

The [riff project](https://github.com/projectriff/riff) provides its own 
[builder](https://github.com/projectriff/builder) that specifically targets building functions using the riff function invokers.

## How it Works

As long as the dependencies are included in the archive correctly, you can supply a `Function` with a wide range of input and output types.
The input or output types can be plain JDK classes, or POJOs defined in your archive, or `Message` 
(from `spring-messaging`) or `Publisher` (from `reactive-streams`) or `Flux` or `Mono` (from `reactor-core`).
Input and output types can also be reactor's `TupleX` classes, thus allowing multi I/O functions.
The `Message` type will give you access to header metadata in the incoming and outgoing messages.
POJOs are converted from incoming messages / to return values using the `Content-Type` header and the `expectedContentType` field value.
