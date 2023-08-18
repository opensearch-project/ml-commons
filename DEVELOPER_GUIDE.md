<p center><img src="https://github.com/opensearch-project/opensearch-py/raw/main/OpenSearch.svg" height="64px" alt="Opensearch"/></p>
<h1 center>ML-Commons Developer Guide</h1>

This guide applies to the development within the ML-Commons project

- [Getting started guide](#getting-started-guide)
    - [Key technologies](#key-technologies)
    - [Prerequisites](#prerequisites)
    - [Fork and clone ML-Commons](#fork-and-clone-ml-commons)
    - [Run OpenSearch](#run-opensearch)
    - [Build](#Build)
      - [Building from the command line](#Building-from-the-command-line)
      - [Debugging](#Debugging)
- [More documentation](#More-docs)
- [Code guidelines](#code-guidelines)




## Getting started guide

This guide is for any developer who wants a running local development environment where you can make, see, and test changes. It's opinionated to get you running as quickly and easily as possible, but it's not the only way to set up a development environment.

If you're only interested in installing and using this plugin features, you can just install Opensearch and ml-commons plugin will be integrated with Opensearch.

If you're planning to contribute code (features or fixes) to this repository, great! Make sure to also read the [contributing guide](CONTRIBUTING.md).


### Key technologies

ml-commons is primarily a Java based plugin for machine learning in opensearch. To effectively contribute you need to be familiar with Java.

### Prerequisites

To develop on ml-commons, you'll need:

- A [GitHub account](https://docs.github.com/en/get-started/onboarding/getting-started-with-your-github-account)
- [`git`](https://git-scm.com/) for version control
- [`Java`](https://www.java.com/en/)
- A code editor of your choice, configured for Java. If you don't have a favorite editor, we suggest [Intellij](https://www.jetbrains.com/idea/)

If you already have these installed or have your own preferences for installing them, skip ahead to the [Fork and clone ml-commons](#fork-and-clone-ml-commons) section.


#### Install `git`

If you don't already have it installed (check with `git --version`) we recommend following [the `git` installation guide for your OS](https://git-scm.com/downloads).

#### Install `Java`

You can install any version of Java starting from 17. [`Jenv`](https://www.jenv.be/) is a good option to use so that you can have multiple versions of Java.


### Fork and clone ml-commons

All local development should be done in a [forked repository](https://docs.github.com/en/get-started/quickstart/fork-a-repo).
Fork ml-commons by clicking the "Fork" button at the top of the [GitHub repository](https://github.com/opensearch-project/ml-commons).

Clone your forked version of ml-commons to your local machine (replace `opensearch-project` in the command below with your GitHub username):

```bash
$ git clone git@github.com:opensearch-project/ml-commons.git
```

### Run OpenSearch

You can install Opensearch multiple ways:

1. https://opensearch.org/downloads.html#docker-compose
2. https://opensearch.org/docs/2.5/install-and-configure/install-opensearch/tar/

#### Default setup for opensearch

```yml
opensearch.hosts: ["https://localhost:9200"]
opensearch.username: "admin" # Default username
opensearch.password: "admin" # Default password
```

### Build

This package uses the [Gradle](https://docs.gradle.org/current/userguide/userguide.html) build system. Gradle comes with excellent documentation that should be your first stop when trying to figure out how to operate or modify the build. we also use the OpenSearch build tools for Gradle. These tools are idiosyncratic and don't always follow the conventions and instructions for building regular Java code using Gradle. Not everything in this package will work the way it's described in the Gradle documentation. If you encounter such a situation, the OpenSearch build tools [source code](https://github.com/opensearch-project/OpenSearch/tree/main/buildSrc/src/main/groovy/org/opensearch/gradle) is your best bet for figuring out what's going on.

#### Building from the command line

1. `./gradlew build` builds and tests, `./gradlew build buildDeb buildRpm` build RPM and DEB.
2. `./gradlew run` launches a single node cluster with ml-commons plugin installed
3. `./gradlew integTest` launches a single node cluster with ml-commons plugin installed and runs all integration tests except security. Use `./gradlew integTest -PnumNodes=<number>` to launch multi-node cluster.
4. ` ./gradlew integTest --tests="<class path>.<test method>"` runs a single integration test class or method, for example `./gradlew integTest --tests="org.opensearch.ml.rest.RestMLTrainAndPredictIT.testTrainAndPredictKmeansWithEmptyParam"` or `./gradlew integTest --tests="org.opensearch.ml.rest.RestMLTrainAndPredictIT"`
5. `./gradlew integTest -Dtests.class="<class path>"` run specific integ test class, for example `./gradlew integTest -Dtests.class="org.opensearch.ml.rest.RestMLTrainAndPredictIT"`
6. `./gradlew integTest -Dtests.method="<method name>"` run specific integ test method, for example `./gradlew integTest -Dtests.method="testTrainAndPredictKmeans"`
7. `./gradlew integTest -Dtests.rest.cluster=localhost:9200 -Dtests.cluster=localhost:9200 -Dtests.clustername="docker-cluster" -Dhttps=true -Duser=admin -Dpassword=admin` launches integration tests against a local cluster and run tests with security. Detail steps: (1)download OpenSearch tarball to local and install by running `opensearch-tar-install.sh`; (2)build ML plugin zip with your change and install ML plugin zip; (3)restart local test cluster; (4) run this gradle command to test.
8. `./gradlew spotlessApply` formats code. And/or import formatting rules in `.eclipseformat.xml` with IDE.
9. `./gradlew adBwcCluster#mixedClusterTask -Dtests.security.manager=false` launches a cluster with three nodes of bwc version of OpenSearch with anomaly-detection and job-scheduler and tests backwards compatibility by upgrading one of the nodes with the current version of OpenSearch with anomaly-detection and job-scheduler creating a mixed cluster.
10. `./gradlew adBwcCluster#rollingUpgradeClusterTask -Dtests.security.manager=false` launches a cluster with three nodes of bwc version of OpenSearch with anomaly-detection and job-scheduler and tests backwards compatibility by performing rolling upgrade of each node with the current version of OpenSearch with anomaly-detection and job-scheduler.
11. `./gradlew adBwcCluster#fullRestartClusterTask -Dtests.security.manager=false` launches a cluster with three nodes of bwc version of OpenSearch with anomaly-detection and job-scheduler and tests backwards compatibility by performing a full restart on the cluster upgrading all the nodes with the current version of OpenSearch with anomaly-detection and job-scheduler.
12. `./gradlew bwcTestSuite -Dtests.security.manager=false` runs all the above bwc tests combined.

When launching a cluster using one of the above commands logs are placed in `/build/cluster/run node0/opensearch-<version>/logs`. Though the logs are teed to the console, in practices it's best to check the actual log file.

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

## More docs

1. [Model serving framework](https://opensearch.org/docs/latest/ml-commons-plugin/model-serving-framework/)
2. [Model Access Control](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/model_access_control.md)
3. [How to add a new function](https://github.com/opensearch-project/ml-commons/blob/2.x/docs/how-to-add-new-function.md)

## Code guidelines

#### Filenames

All filenames should use `CamelCase`.

**Right:** `ml-commons/common/src/main/java/org.opensearch/ml/common/MLModelGroup.java`

**Wrong:** `ml-commons/common/src/main/java/org.opensearch/ml/common/ml_model_group.java`

#### Do not comment out code

We use a version management system. If a line of code is no longer needed,
remove it, don't simply comment it out.

#### Avoid global definitions

Don't do this. Everything should be wrapped in a module that can be depended on
by other modules. Even things as simple as a single value should be a module.

#### Write small functions

Keep your functions short. A good function fits on a slide that the people in
the last row of a big room can comfortably read. So don't count on them having
perfect vision and limit yourself to ~25 lines of code per function.
