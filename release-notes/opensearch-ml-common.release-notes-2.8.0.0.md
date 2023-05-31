## Version 2.8.0.0 Release Notes

Compatible with OpenSearch 2.8.0

### Experimental Features

* Model access control. ([#928](https://github.com/opensearch-project/ml-commons/pull/928))

### Enhancements

* Add a setting to enable/disable model url in register API ([#871](https://github.com/opensearch-project/ml-commons/pull/871))
* Add a setting to enable/disable local upload while registering model ([#873](https://github.com/opensearch-project/ml-commons/pull/873))
* Check hash value for the pretrained models ([#878](https://github.com/opensearch-project/ml-commons/pull/878))
* Add pre-trained model list ([#883](https://github.com/opensearch-project/ml-commons/pull/883))
* Add content hash value for the correlation model. ([#885](https://github.com/opensearch-project/ml-commons/pull/885))
* Set default access_control_enabled setting to false ([#935](https://github.com/opensearch-project/ml-commons/pull/935))
* Enable model access control in secure reset IT ([#940](https://github.com/opensearch-project/ml-commons/pull/940))
* Add model group rest ITs ([#942](https://github.com/opensearch-project/ml-commons/pull/942))

### Bug Fixes

* Fix class not found exception when deserialize model ([#899](https://github.com/opensearch-project/ml-commons/pull/899))
* Fix publish shadow publication dependency issue ([#919](https://github.com/opensearch-project/ml-commons/pull/919))
* Fix model group index not existing model version query issue and SecureMLRestIT failure ITs ([#933](https://github.com/opensearch-project/ml-commons/pull/933))
* Fix model access mode upper case bug ([#937](https://github.com/opensearch-project/ml-commons/pull/937))

### Documentation


### Maintenance

* Increment version to 2.8.0-SNAPSHOT ([#896](https://github.com/opensearch-project/ml-commons/pull/896))

### Refactoring

* Change mem_size_estimation to memory_size_estimation ([#868](https://github.com/opensearch-project/ml-commons/pull/868))
