# How to add new algorithm

This doc explains how to add new algorithm to `ml-commons` with two examples.

## Example 1 - Sample algorithm with train/predict APIs
This sample algorithm will train a dummy model first. Then user can call predict API to calculate total sum of a data frame or selected data from indices.


### Step 1: add parameter class for new algorithm 
Add new enum in `MLAlgoName`
```
public class MLAlgoName {
    LINEAR_REGRESSION("linear_regression"),
    KMEANS("kmeans"),
    
    //Add new sample algorithm name
    SAMPLE_ALGO("sample_algo"),
    ...
}
```

Create new class `SampleAlgoParams` in `common` package by implements `MLAlgoParams` interface. 
Must define `NamedXContentRegistry.Entry` in `SampleAlgoParams`, sample code(check more details in ml-commons code)
```
public class SampleAlgoParams implements MLAlgoParams {

    public static final String PARSE_FIELD_NAME = "sample_algo";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );
    ...
    // find more details in code
}
```
Register `SampleAlgoParams.XCONTENT_REGISTRY` in `MachineLearningPlugin.getNamedXContent()` method
```
    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return ImmutableList
                .of(
                        ...
                        SampleAlgoParams.XCONTENT_REGISTRY,
                        ...
                );
    }
```
### Step 2: add output class for new algorithm
Add new enum in `MLOutput.MLOutputType`:
```    
    public enum MLOutputType {
        ...
        SAMPLE_ALGO("SAMPLE_ALGO");
        ...
    }
```
Create new class `SampleAlgoOutput` in `common` package by extending abstract class `MLOutput`.

### Step 3: add new algorithm in `ml-algorithms` package
Create new class `ml-algorithms/src/main/java/org/opensearch/ml/engine/algorithms/sample/SampleAlgo.java` by implementing interface `MLAlgo`.
Override `train`, `predict` methods.

### Step 4: configure new algorithm
Add new input/output in config file `common/src/main/resources/ml-commons-config.yml` 

Key is input/output enum name, value is class name.
```
ml_algo_param_class:
  sample_algo: org.opensearch.ml.common.parameter.SampleAlgoParams
  ...

ml_output_class:
  sample_algo: org.opensearch.ml.common.parameter.SampleAlgoOutput
  ...
```

Add new ML algorithm in config file `ml-algorithms/src/main/resources/ml-algorithm-config.yml`

Key is ML algorithm enum name, value is class name.
```
ml_algo_class:
  sample_algo: org.opensearch.ml.engine.algorithms.sample.SampleAlgo
  ...

```

### Step 5: Run and test
Run `./gradlew run` and test sample algorithm.

Train with sample data
```
# Train
POST /_plugins/_ml/_train/sample_algo
{
    "parameters": {
        "sample_param": 22
    },
    "input_data": {
        "column_metas": [
            { "name": "total_sum","column_type": "DOUBLE" },
            { "name": "is_error","column_type": "BOOLEAN" }
        ],
        "rows": [
            {
                "values": [
                    { "column_type": "DOUBLE","value": 15 },
                    { "column_type": "BOOLEAN","value": false }
                ]
            }
        ]
    }
}

# Sample response
{
    "model_id": "247c5947-35a1-41a7-a95b-703a1e9b2203",
    "status": "CREATED"
}
```

Predict with sample data
```
# Load test data
POST /_bulk
{ "index" : { "_index" : "predict_data" } }
{"k1":0.0,"k2":1.0}
{ "index" : { "_index" : "predict_data" } }
{"k1":12.0,"k2":3.0}

# Predict
POST _plugins/_ml/_predict/sample_algo/247c5947-35a1-41a7-a95b-703a1e9b2203
{
    "parameters": {
        "sample_param": 22
    },
    "input_query": {
        "query": { "bool": { "filter": [ {"range": {"k1": {"gte": 0}}} ] } }
    },
    "input_index": ["predict_data"]
}

# Sample output: total sum of [[0.0, 1.0], [12.0, 3.0]]
{
    "sample_result": 16.0
}
```

## Example 2 - Sample algorithm(no model) with execute API
Some algorithm like anomaly localization has no model. We can add such algorithm by exposing execute API only.
In this example, we will add a new sample calculator which runs on local node (don't dispatch task to other node).
The sample calculator supports calculating `sum` or `max` value of a data frame or selected data from indices.

### Step 1: add input class for new algorithm
Add new class `org.opensearch.ml.common.parameter.LocalSampleCalculatorInput` by implementing `Input`.
Must define `NamedXContentRegistry.Entry` in `LocalSampleCalculatorInput`.
```
public class LocalSampleCalculatorInput implements Input {
    public static final String PARSE_FIELD_NAME = "local_sample_calculator";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            Input.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );
    ...
    //check more details in ml-commons package
}
```

Register `SampleAlgoParams.XCONTENT_REGISTRY` in `MachineLearningPlugin.getNamedXContent()` method
```
    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return ImmutableList
                .of(
                        ...
                        LocalSampleCalculatorInput.XCONTENT_REGISTRY
                );
    }
```

### Step 2: add output class for new algorithm
Add output class `org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput` by implementing `Output`.
```
public class LocalSampleCalculatorOutput implements Output{
    private Double result;
    ...
}
```

### Step 3: add new algorithm in `ml-algorithms` package
Create new class `ml-algorithms/src/main/java/org/opensearch/ml/engine/algorithms/sample/LocalSampleCalculator.java` 
by implementing interface `Executable`. Override `execute` method.

```
public class LocalSam**pleCalculator implements Executable {
    @Override
    public Output execute(Input input) {
        ....
        // find more details in code
    }
}
```

### Step 4: configure new algorithm
Add new algorithm/function name in enum `org.opensearch.ml.common.parameter.MLAlgoName`(may change the class name later)
```
public enum MLAlgoName {
    LINEAR_REGRESSION("linear_regression"),
    KMEANS("kmeans"),
    ...
    // Add new enum
    LOCAL_SAMPLE_CALCULATOR("local_sample_calculator");
}
```

Add new executable function in config file `ml-algorithms/src/main/resources/ml-algorithm-config.yml`

Key is the new enum name `local_sample_calculator`, value is class name.
```
executable_function_class:
  local_sample_calculator: org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator
```
### Step 5: Run and test
Run `./gradlew run` and test this sample calculator.

```
POST _plugins/_ml/_execute/local_sample_calculator
{
    "operation": "max",
    "input_data": [1.0, 2.0, 3.0]
}

# Output
{
    "sample_result": 3.0
}
```
