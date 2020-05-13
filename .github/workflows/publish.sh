#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

version=${1:-$(./mvnw -B help:evaluate -Dexpression=project.version -q -DforceStdout | tail -n1)}
commit=$(git rev-parse HEAD)

package=java-function-invoker-${version}.jar
bucket=gs://projectriff/java-function-invoker/releases

pushd target
  gsutil cp "${package}" ${bucket}/v${version}/${package}
  gsutil cp "${package}" ${bucket}/v${version}/snapshots/java-function-invoker-${version}-${commit}.jar
  gsutil cp "${package}" ${bucket}/latest/java-function-invoker.jar
popd
