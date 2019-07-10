#!/bin/bash

version=${1:-$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)}
commit=$(git rev-parse HEAD)

gcloud auth activate-service-account --key-file <(echo "$GCLOUD_CLIENT_SECRET" | base64 --decode)

package=java-function-invoker-${version}.jar
bucket=gs://projectriff/java-function-invoker/releases

gsutil cp -a public-read "${package}" ${bucket}/v${version}/${package}
gsutil cp -a public-read "${package}" ${bucket}/v${version}/snapshots/java-function-invoker-${version}-${commit}.tgz
gsutil cp -a public-read "${package}" ${bucket}/latest/java-function-invoker.jar

