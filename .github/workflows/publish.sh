#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

version=${1:-$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout | tail -n1)}
commit=$(git rev-parse HEAD)

package=java-function-invoker-${version}.jar
bucket=gs://projectriff/java-function-invoker/releases

pushd target
  gsutil cp -a public-read "${package}" ${bucket}/v${version}/${package}
  gsutil cp -a public-read "${package}" ${bucket}/v${version}/snapshots/java-function-invoker-${version}-${commit}.jar
  gsutil cp -a public-read "${package}" ${bucket}/latest/java-function-invoker.jar
popd
