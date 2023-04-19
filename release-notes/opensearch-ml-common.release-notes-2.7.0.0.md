## Version 2.7.0.0 Release Notes

Compatible with OpenSearch 2.7.0

### Experimental Features

* Add metrics correlation algorithm. ([#845](https://github.com/opensearch-project/ml-commons/pull/845))

### Enhancements

* Add model auto deploy feature ([#852](https://github.com/opensearch-project/ml-commons/pull/852))
* Add memory consumption estimation for models in profile API ([#853](https://github.com/opensearch-project/ml-commons/pull/853))
* Add text docs ML input ([#830](https://github.com/opensearch-project/ml-commons/pull/830))
* Add allow custom deployment plan setting; add deploy to all nodes field in model index ([#818](https://github.com/opensearch-project/ml-commons/pull/818))
* Add exclude nodes setting. ([#813](https://github.com/opensearch-project/ml-commons/pull/813))
* set model state as partially loaded if unload model from partial nodes ([#806](https://github.com/opensearch-project/ml-commons/pull/806))

### Bug Fixes

* change to old method to fix missing method createParentDirectories ([#759](https://github.com/opensearch-project/ml-commons/pull/759))
* fix delete model API ([#861](https://github.com/opensearch-project/ml-commons/pull/861))
* fix breaking changes of Xcontent namespace change ([#838](https://github.com/opensearch-project/ml-commons/pull/838))
* Change the ziputil dependency to fix a potential security concern ([#824](https://github.com/opensearch-project/ml-commons/pull/824))
* fix checkstyle version ([#792](https://github.com/opensearch-project/ml-commons/pull/792))
* Typo fix and minor improvement in maven-publish GHA workflow ([#757](https://github.com/opensearch-project/ml-commons/pull/757))

### Documentation

* add docker-compose file for starting cluster with dedicated ML node ([#799](https://github.com/opensearch-project/ml-commons/pull/799))

### Maintenance

* Increment version to 2.7.0-SNAPSHOT ([#742](https://github.com/opensearch-project/ml-commons/pull/742))
* Publish snapshots to maven via GHA ([#754](https://github.com/opensearch-project/ml-commons/pull/754))

### Refactoring

* rename API/function/variable names ([#822](https://github.com/opensearch-project/ml-commons/pull/822))
* rename model meta/chunk API ([#827](https://github.com/opensearch-project/ml-commons/pull/827))
