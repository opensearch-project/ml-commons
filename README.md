<img src="https://opensearch.org/assets/brand/SVG/Logo/opensearch_logo_default.svg" height="64px"/>

<!-- TOC -->

- [OpenSearch Machine Learning Commons](#opensearch-machine-learning-commons)
- [Contributing](#contributing)
- [Code of Conduct](#code-of-conduct)
- [Security](#security)
- [License](#license)
- [Copyright](#copyright)

<!-- /TOC -->

## OpenSearch Machine Learning Commons

Machine Learning Commons for OpenSearch is a new solution that make it easy to develop new machine learning feature. It allows engineers to leverage existing opensource machine learning algorithms and reduce the efforts to build any new machine learning feature. It also removes the necessity from engineers to manage the machine learning tasks which will help to speed the feature developing process.

### Problem Statement

Until today, the challenge is significant to build a new machine learning feature inside OpenSearch. The reasons include:

* **Disruption to OpenSearch Core features**. Machine learning is very computationally intensive. But currently there is no way to add dedicated computation resources in OpenSearch for machine learning jobs, hence these jobs have to share same resources with Core features, such as: indexing and searching. That might cause the latency increasing on search request, and cause circuit breaker exception on memory usage. To address this, we have to carefully distribute models and limit the data size to run the AD job. When more and more ML features are added into OpenSearch, it will become much harder to manage.
* **Lack of support for machine learning algorithms.** Customers need more algorighms within Opensearch, otherwise the data need be exported to outside of elasticsearch, such as s3 first to do the job, which will bring extra cost and latency.
* **Lack of resource management mechanism between multiple machine learning jobs.** It's hard to coordinate the resources between multi features.


In the meanwhile, we observe more and more machine learning features required to be supported in OpenSearch to power end users’ business needs. For instance:

* **Forecasting**: Forecasting is very popular in time series data analysis. Although the past data isn’t always an indicator for the future, it’s still very powerful tool used in some use cases, such as capacity planning to scale up/down the service hosts in IT operation.
* **Root Cause Analysis in DevOps**: Today some customers use OpenSearch for IT operations. It becomes more and more complicated to identify the root cause of an outage or incident since it needs to gather all the information in the ecosystem, such as log, traces, metrics. Machine learning technique is a great fit to address this issue by building topology models of the system automatically, and understanding the similarity and casual relations between events, etc.
* **Machine Learning in SIEM**: SIEM(Security Information and Event Management) is another domain in OpenSearch. Machine learning is also very useful in SIEM to help facilitate security analytics, and it can reduce the effort on sophisticated tasks, enable real time threat analysis and uncover anomalies.

### Solution
The solution is to introduce a new Machine Learning library inside the OpenSearch cluster. The major functionalities in this solution include:

* **Unified Client Interfaces:** clients can use common interfaces for training and inference tasks, and then follow the algorithm interface to give right input parameters, such as input data, hyperparameters. A client library will be built for easy use.
* **ML Plugin:** ML plugin will help to initiate the ML nodes, and choose the right nodes and allocate the resources for each request, and manage machine learning tasks with monitoring and failure handing supports, and store the model results; it will be the bridge for the communication between OpenSearch process and ML engine.
* **ML Engine**: This engine will be the host for ML algorithms.  Java based machine learning algorithms will be supported in the first release.

This solution makes it easy to develop new machine learning features. It allows engineers to leverage existing open-source machine learning algorithms, and reduce the efforts to build any new machine learning feature. It also removes the necessity from engineers to manage the machine learning tasks which will help to speed up the feature developing process.

### How to use it for new feature development

As mentioned above, new interfaces including both prediction and training will be provided to customers through Rest APIs, and to other plugins through transport action. Here are the transport action for prediction and training interfaces.

* Predict Transport Action for prediction job request
  ```
  Request: {
        "algorithm": "ARIMA",  //the name of algorithm
        "parameters": {"forecasts_en":10, "seasonal"=true}, // parameters of the algorithm, can be null or empty
        "modelId":123, //the id for trainded model.
        "inputData": [[1.0, 2, 3.1, true, "v1"],[1.1, 4, 5.2, false, "v2"]] // internal data frame interface
    }
    
    Response: {
        "taskId": "123", //the id of the job request
        "status": "SUCCESS", // the job execution status
        "predictionResult": [[6.0],[7.0]] // internal data frame interface
    }
   ```      
* Training Transport Action to start training job request - Async Interface
  ```
  Request: {
     "algorithm": "ARIMA", //the name of algorithm
     "parameters": {"forecasts_en":10, "seasonal"=true}, // parameters of the algorithm, can be null or empty
     "inputData": [[1.0, 2, 3.1, true, "v1"],[1.1, 4, 5.2, false, "v2"]] // internal data frame interface
    }
    
    
    Response: {
     "taskId": "123", //the id of the job request
     "status": "IN_PROGRESS" // the job execution status
    
    }
   ```

## Contributing

See [developer guide](DEVELOPER_GUIDE.md) and [how to contribute to this project](CONTRIBUTING.md).

## Code of Conduct

This project has adopted the [Amazon Open Source Code of Conduct](CODE_OF_CONDUCT.md). For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq), or contact [opensource-codeofconduct@amazon.com](mailto:opensource-codeofconduct@amazon.com) with any additional questions or comments.

## Security

If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public GitHub issue.

## License

This project is licensed under the [Apache v2.0 License](LICENSE).

## Copyright

Copyright 2020-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.

