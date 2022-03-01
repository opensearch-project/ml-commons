- [Developer Guide](#developer-guide)
    - [Forking and Cloning](#forking-and-cloning)
    - [Install Prerequisites](#install-prerequisites)
        - [JDK 14](#jdk-14)
    - [Setup](#setup)
    - [Build](#build)
        - [Building from the command line](#building-from-the-command-line)
        - [Building from the IDE](#building-from-the-ide)
        - [Debugging](#debugging)

## Developer Guide

### Forking and Cloning

Fork this repository on GitHub, and clone locally with `git clone`.

### Install Prerequisites

#### JDK 14

OpenSearch components build using Java 14 at a minimum. This means you must have a JDK 14 installed with the environment variable `JAVA_HOME` referencing the path to Java home for your JDK 14 installation, e.g. `JAVA_HOME=/usr/lib/jvm/jdk-14`.

### Setup

1. Clone the repository (see [Forking and Cloning](#forking-and-cloning))
2. Make sure `JAVA_HOME` is pointing to a Java 14 JDK (see [Install Prerequisites](#install-prerequisites))
3. Launch Intellij IDEA, Choose Import Project.

### Build

This package uses the [Gradle](https://docs.gradle.org/current/userguide/userguide.html) build system. Gradle comes with excellent documentation that should be your first stop when trying to figure out how to operate or modify the build. we also use the OpenSearch build tools for Gradle. These tools are idiosyncratic and don't always follow the conventions and instructions for building regular Java code using Gradle. Not everything in this package will work the way it's described in the Gradle documentation. If you encounter such a situation, the OpenSearch build tools [source code](https://github.com/opensearch-project/OpenSearch/tree/main/buildSrc/src/main/groovy/org/opensearch/gradle) is your best bet for figuring out what's going on.

#### Building from the command line

1. `./gradlew build` builds and tests
2. `./gradlew :run` launches a single node cluster with ml-commons plugin installed
3. `./gradlew :integTest` launches a single node cluster with ml-commons plugin installed and runs all integration tests except security. Use `./gradlew integTest -PnumNodes=<number>` to launch multi-node cluster.
4. ` ./gradlew :integTest --tests="<class path>.<test method>"` runs a single integration test class or method, for example `./gradlew integTest --tests="org.opensearch.ml.rest.RestMLTrainAndPredictIT.testTrainAndPredictKmeansWithEmptyParam"` or `./gradlew integTest --tests="org.opensearch.ml.rest.RestMLTrainAndPredictIT"`
5. `./gradlew integTest -Dtests.class="<class path>"` run specific integ test class, for example `./gradlew integTest -Dtests.class="org.opensearch.ml.rest.RestMLTrainAndPredictIT"`
6. `./gradlew integTest -Dtests.method="<method name>"` run specific integ test method, for example `./gradlew integTest -Dtests.method="testTrainAndPredictKmeans"`
7. `./gradlew integTest -Dtests.rest.cluster=localhost:9200 -Dtests.cluster=localhost:9200 -Dtests.clustername="docker-cluster" -Dhttps=true -Duser=admin -Dpassword=admin` launches integration tests against a local cluster and run tests with security. Detail steps: (1)download OpenSearch tarball to local and install by running `opensearch-tar-install.sh`; (2)build ML plugin zip with your change and install ML plugin zip; (3)restart local test cluster; (4) run this gradle command to test.  
8. `./gradlew spotlessApply` formats code. And/or import formatting rules in `.eclipseformat.xml` with IDE.

When launching a cluster using one of the above commands logs are placed in `/build/cluster/run node0/opensearch-<version>/logs`. Though the logs are teed to the console, in practices it's best to check the actual log file.

#### Building from the IDE

Currently, the only IDE we support is IntelliJ IDEA.  It's free, it's open source, it works. The gradle tasks above can also be launched from IntelliJ's Gradle toolbar and the extra parameters can be passed in via the Launch Configurations VM arguments.

#### Debugging

Sometimes it's useful to attach a debugger to either the OpenSearch cluster or the integ tests to see what's going on. When running unit tests you can just hit 'Debug' from the IDE's gutter to debug the tests.  To debug code running in an actual server run:

```
./gradlew :integTest --debug-jvm # to start a cluster and run integ tests
OR
./gradlew :run --debug-jvm # to just start a cluster that can be debugged
```

The OpenSearch server JVM will launch suspended and wait for a debugger to attach to `localhost:8000` before starting the OpenSearch server.

To debug code running in an integ test (which exercises the server from a separate JVM) run:

```
./gradlew -Dtest.debug :integTest 
```

The test runner JVM will start suspended and wait for a debugger to attach to `localhost:5005` before running the tests.