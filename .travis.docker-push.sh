#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

# TODO use something more robust than an regex
version=`cat pom.xml | perl -wnE'say for /<docker.tag>([^<]+)<\/docker.tag>/g'`

./mvnw dockerfile:build -Ddocker.tag=latest
docker tag "projectriff/java-function-invoker:latest" "projectriff/java-function-invoker:${version}"
docker tag "projectriff/java-function-invoker:latest" "projectriff/java-function-invoker:${version}-ci-${TRAVIS_COMMIT}"

docker login -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD"
docker push "projectriff/java-function-invoker"
