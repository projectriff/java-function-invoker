# Java Function Invoker [![Build Status](https://travis-ci.com/projectriff/java-function-invoker.svg?branch=master)](https://travis-ci.com/projectriff/java-function-invoker)

## What It Does

The "Java Function Invoker" lets you concentrate on writing your business logic as a Java function while the invoker takes care of the rest that is needed to run your functions in a Kubernetes cluster with riff and Knative installed.
The invoker is a [Spring Boot](https://projects.spring.io/spring-boot) application that will locate your function in the JAR file you provide based on some configuration settings.
It will then invoke the function for each request.
When used in a function service like [riff on Knative](https://projectriff.io/), the invoker boot application is provided by the platform when functions are built.

## How To Use It

### Function source

You need to configure a Maven or Gradle project for your function.
If you use Spring Boot then we recommend using [Spring Initializr](https://start.spring.io/) to bootstrap your project.
If you are not using Spring Boot for your function code then you need to create your own build configuration.
There are no required dependencies from the Java Function Invoker, it only requires that your function implements the `java.util.function.Function` interface.

The [Spring Cloud Function](https://cloud.spring.io/spring-cloud-function/) project provides support for writing functions as part of a Spring Boot app.
The Java Function Invoker uses Spring Cloud Function to invoke your functions, so most features available with Spring Cloud Function are also available with the Java Function Invoker.

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
If you have multiple functions in the source then you have to specify which one you want using a handler (see the next section).

If you have a Plain Java class with a function then you can provide a `Function-Class` entry in the JAR file manifest to indicate which function class to use.
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
More complex creation scenarios can be handled by giving the handler in the form `<bean>[&main=<main>]` where

* `<bean>` is a class name or bean name, and
* `<main` is a Spring `@Configuration` class to create an application context

If you provide a `main` parameter you need to include Spring Boot and all the other dependencies of the context in your archive.
A Spring Boot jar file does not need an explicit `main` (there is one in the `MANIFEST.MF`), but if you supply one it will be used.
If the `Function` has POJO (i.e. not generic JDK classes) as parameter types, then you also need to depend on `spring-cloud-function-context` (and include that in the archive).

Example:

```
handler=functions.Greeter
```

Example with Spring Boot application:

```
handler=greeter
```

Example with Spring application context and an explicit main

```
handler='greeter&main=functions.FunctionApp'
```

#### Function samples

We have sample application for [plain Java function](https://github.com/projectriff/java-function-invoker/tree/master/samples/greeter) and for [Spring Boot app with a function bean](https://github.com/projectriff/java-function-invoker/tree/master/samples/uppercase).

### Cloud Native Buildpacks

[Buildpacks](https://buildpacks.io/) provide a higher-level abstraction for building apps compared to Dockerfiles.

The [riff project](https://github.com/projectriff/riff) provides its own [buildpack](https://github.com/projectriff/riff-buildpack) that specifically targets building functions using the riff function invokers.

## How it Works

As long as the dependencies are included in the archive correctly, you can supply a `Function` with a wide range of input and output types.
The input or output types can be plain JDK classes, or POJOs defined in your archive, or `Message` (from `spring-messaging`) or `Publisher` (from `reactive-streams`) or `Flux` or `Mono` (from `reactor-core`).
The `Message` type will give you access to header metadata in the incoming and outgoing messages.
If the input or output is either a `Publisher` or a `Message` (or a `Publisher<Message>`) then both input and output must be in the same form, with possibly different payload types, obviously. POJOs are converted from incoming messages assuming the payload is JSON and using the GSON library.

The invoker is a [Spring Boot](https://projects.spring.io/spring-boot) application with a configuration key `function.uri` that can be used to point to a `java.util.function.Function`.
Because of the way Spring Boot works you can use an environment variable `FUNCTION_URI` or a System property `function.uri` or a command line argument `--function.uri` (amongst other options).
Its value points to a classpath archive, which can be a jar file or a directory, together with parameters:

* `handler`: the class name of a `Function` to execute, or a bean name of a `Function`. Can also be a comma, or pipe-separated list of functions, which are composed together at runtime.

* `main`: (optional) the class name of a Spring `@Configuration` that can be used to create a Spring application context, in which the `handler` is defined.

The jar archive can also be a comma-separated list of archive locations, in case you need to compose things together.

> NOTE: If your Spring Boot application contains a single function bean, then you can omit the `handler` and `main` parameters since the function can be automatically detected. You can also omit the the `handler` and `main` parameters if the JAR manifest has a `Function-Class` entry.

Examples:

* A jar file

```
file:target/app.jar?handler=functions.Greeter
```

* A Spring app (with `spring-cloud-function-context`) and function specified by bean class

```
file:target/app.jar?handler=functions.Greeter&main=functions.Application
```

* A Spring app and function specified by bean name

```
file:target/app.jar?handler=greeter&main=functions.Application
```

* A Spring app split between 2 jar files

```
file:target/app.jar,file:target/lib.jar?handler=greeter&main=functions.Application
```
