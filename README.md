
# Java Function Invoker [![Build Status](https://travis-ci.org/projectriff/java-function-invoker.svg?branch=master)](https://travis-ci.org/projectriff/java-function-invoker)

## What It Does

The "Java Function Invoker" let's you concentrate on writing your business logic as a Java function while the invoker takes care of the rest that is needed to run your functions in a Kubernetes cluster with riff and Knative installed.
The invoker is a [Spring Boot](https://projects.spring.io/spring-boot) application that will locate your function in the JAR file you provide based on some configuration settings.
It will then invoke the function for each request.

## How To Use It

### Function source

You need to configure a Maven or Gradle project for your function.
If you use Spring Boot then we recommend using [Spring Initializr](https://start.spring.io/) to bootsrap your project.
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

You also need a small config file that specifies the startup class or bean for your function.
Create a `riff.toml` file in the base directory of your app source with the following content:

```
handler = "uppercase"
```

#### Function handler

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
handler=greeter&main=functions.FunctionApp
```

#### Function samples

We have sample application for [plain Java function](https://github.com/projectriff/java-function-invoker/tree/master/samples/greeter) and for [Spring Boot app with a function bean](https://github.com/projectriff/java-function-invoker/tree/master/samples/uppercase).

### Cloud Native Buildpacks

[Buildpacks](https://buildpacks.io/) provide a higher-level abstraction for building apps compared to Dockerfiles.

The [riff project](https://github.com/projectriff/riff) provides its own [buildpack](https://github.com/projectriff/riff-buildpack) that specifically targets building functions using the riff function invokers.

For the remainder of this document we will be using the riff buildpack to build and package our functions.

### Building and testing locally

You can use the `pack` CLI to build and test your functions locally.
Install the CLI from the [buildpack/pack release page](https://github.com/buildpack/pack/releases).

From the base directory of your app source, you can build locally using:

```sh
pack build --builder projectriff/builder --path . dev.local/upper
```

When the build completes you should have a new Docker image named `dev.local/upper` created.
We can start that image using:

```sh
docker run -p 8080:8080 dev.local/upper:latest
```

Once the app starts up we can send a request to the app and see the reply:

```sh
curl localhost:8080 -H 'Content-Type: text/plain' -w '\n' -d hello
HELLO
```

### Building and deploying functions locally

To build and deploy your function locally you can use the `riff` CLI which can be installed following the instructions on the [riff Release page](https://github.com/projectriff/riff/releases).

You also need to have riff and Knative installed on a local [Minikube](https://projectriff.io/docs/getting-started/minikube/) or [Docker for Mac](https://projectriff.io/docs/getting-started/docker-for-mac/) cluster, just follow the instructions we linked to.

Make sure that you initialize the default namespace with your Docker ID and that you enter your password when prompted:

```sh
export DOCKER_ID=<your Docker ID>
riff namespace init default --dockerhub $DOCKER_ID
```

If you are using Minikube then you should configure Docker to use the Docker environment running in Minikube:

```sh
eval $(minikube docker-env)
```

Now you can build and deploy your function from the base directory of your app source using:

```sh
riff function create java upper --local-path . --image dev.local/upper:v1
```

You need to provide a tag for the image to avoid Kubernetes trying to download the latest version of the image.
If the specified image tag already exists in the Docker daemon then Kubernetes will use it since `IfNotPresent` is the default pull policy.

Once the function is up and running you can invoke it using:

```sh
riff service invoke upper --text -- -w '\n' -d "hello world"
```

To delete the function use:

```sh
riff service delete upper
```

### Building and deploying functions to remote cluster

To build and deploy your function to a remote cluster you can use the `riff` CLI which can be installed following the instructions on the [riff Release page](https://github.com/projectriff/riff/releases).

You also need to have riff and Knative installed on a Kubernetes cluster.
We provide instructions for Google Cloud Platform's Kubernetes Engine (GKE)](https://projectriff.io/docs/getting-started/gke/), just follow the instructions we linked to.

Make sure that you initialize the default namespace with the JSON key file (gcr-storage-admin.json) you created during the riff installation:

```sh
riff namespace init default --gcr gcr-storage-admin.json
```

You need to push your function source to a Git repo and provide the URL for the command that creates the function.

Now you can build and deploy your function from this Git repo using:

```sh
export GCP_PROJECT=$(gcloud config get-value core/project)
export GIT_REPO=<your Git repo URL>
riff function create java upper --git-repo $GIT_REPO --image gcr.io/$GCP_PROJECT/upper --verbose
```

Once the function is up and running you can invoke it using:

```sh
riff service invoke upper --text -- -w '\n' -d "hello world"
```

To delete the function use:

```sh
riff service delete upper
```

## How it Works

As long as the dependencies are included in the archive correctly, you
can supply a `Function` with a wide range of input and output
types. The input or output types can be plain JDK classes, or POJOs
defined in your archive, or `Message` (from `spring-messaging`) or
`Publisher` (from `reactive-streams`) or `Flux` or `Mono` (from
`reactor-core`). The `Message` type will give you access to header
metadata in the incoming and outgoing messages. If the input or output
is either a `Publisher` or a `Message` (or a `Publisher<Message>`)
then both input and output must be in the same form, with possibly
different payload types, obviously. POJOs are converted from incoming
messages assuming the payload is JSON ans using the GSON library.

The invoker is a [Spring Boot](https://projects.spring.io/spring-boot)
application with a configuration key `function.uri` that can be used
to point to a `java.util.function.Function`. Because of the way Spring
Boot works you can use an environment variable `FUNCTION_URI` or a
System property `function.uri` or a command line argument
`--function.uri` (amongst other options). Its value points to a
classpath archive, which can be a jar file or a directory, together
with parameters:

* `handler`: the class name of a `Function` to execute, or a bean name of a `Function`. Can also be a
  comma, or pipe-separated list of functions, which are composed
  together at runtime.
* `main`: (optional) the class name of a Spring `@Configuration` that
  can be used to create a Spring application context, in which the
  `handler` is defined.

The jar archive can also be a comma-separated list of archive
locations, in case you need to compose things together.

Examples:

* A jar file

```
file:target/app.jar&handler=functions.Greeter
```

* A Spring app (with `spring-cloud-function-context`) and function specified by bean class

```
file:target/app.jar&handler=functions.Greeter&main=functions.Application
```

* A Spring app and function specified by bean name

```
file:target/app.jar&handler=greeter&main=functions.Application
```

* A Spring app split between 2 jar files

```
file:target/app.jar,file:target/lib.jar&handler=greeter&main=functions.Application
```
