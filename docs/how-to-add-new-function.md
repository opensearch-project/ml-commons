# How to add new function

This doc explains how to add new function to `ml-commons` with two examples.

## Example 1 - Sample ML algorithm with train/predict APIs
This sample algorithm will train a dummy model first. Then user can call predict API to calculate total sum of a data frame or selected data from indices.

### Step 1: name the function
Add new function name in `org.opensearch.ml.common.parameter.FunctionName`
```
public enum FunctionName {
    ...
    //Add new sample algorithm name
    SAMPLE_ALGO("sample_algo"),
    ...
}
```

### Step 2: add input class 
Create new class `org.opensearch.ml.common.parameter.SampleAlgoParams` in `common` package by implementing `MLAlgoParams` interface. 

Must define `NamedXContentRegistry.Entry` in `SampleAlgoParams`. 

Must add `@MLAlgoParameter` annotation with new algorithm name.
```
@MLAlgoParameter(algorithms={FunctionName.SAMPLE_ALGO}) // must add this annotation
public class SampleAlgoParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.SAMPLE_ALGO.getName();
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

### Step 3: add output class
Add new enum in `org.opensearch.ml.common.parameter.MLOutputType`:
```    
    public enum MLOutputType {
        ...
        SAMPLE_ALGO("sample_algo"),
        ...
    }
```
Create new class `org.opensearch.ml.common.parameter.SampleAlgoOutput` in `common` package by extending abstract class `MLOutput`.

Must add `@MLAlgoOutput` annotation with new output type.

```
@Getter
@MLAlgoOutput(MLOutputType.SAMPLE_ALGO) // must add this annotation
public class SampleAlgoOutput extends MLOutput{
    ...
    private Double sampleResult;
    ...    
}
```

### Step 4: add implementation
Create new class `org.opensearch.ml.engine.algorithms.sample.SampleAlgo` in `ml-algorithms` package by implementing interface `MLAlgo`.
Override `train`, `predict` methods.

Must add `@Function` annotation with new ML algorithm name.
```
@Function(FunctionName.SAMPLE_ALGO) // must add this annotation
public class SampleAlgo implements MLAlgo {
    ...
    private int sampleParam;
    ...
}
```


### Step 5: Run and test
Run `./gradlew run` and test sample algorithm.

Train with sample data
```
# Train
POST /_plugins/_ml/_train/sample_algo
{
    "parameters": {
        "sample_param": 10
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

## Example 2 - Sample calculator with execute API
Some function like anomaly localization has no model. We can add such function by exposing execute API only.
In this example, we will add a new sample calculator which runs on local node (don't dispatch task to other node).
The sample calculator supports calculating `sum`/`max`/`min` value from a double list.

### Step 1: name the function
Add new function name in  `org.opensearch.ml.common.parameter.FunctionName`
```
public enum FunctionName {
    ...
    // Add new enum
    LOCAL_SAMPLE_CALCULATOR("local_sample_calculator");
}
```

### Step 2: add input class
Add new class `org.opensearch.ml.common.parameter.LocalSampleCalculatorInput` by implementing `Input`.
Must define `NamedXContentRegistry.Entry` in `LocalSampleCalculatorInput`.
```
public class LocalSampleCalculatorInput implements Input {
    public static final String PARSE_FIELD_NAME = FunctionName.LOCAL_SAMPLE_CALCULATOR.getName();
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

### Step 3: add output class
Add output class `org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput` by implementing `Output`.
```
public class LocalSampleCalculatorOutput implements Output{
    private Double result;
    ...
}
```

### Step 4: add implementation
Create new class `ml-algorithms/src/main/java/org/opensearch/ml/engine/algorithms/sample/LocalSampleCalculator` in `ml-algorithms` package 
by implementing interface `Executable`. Override `execute` method.

Must add `@Function` annotation with new function name.
```
@Function(FunctionName.LOCAL_SAMPLE_CALCULATOR) // must add this annotation
public class LocalSampleCalculator implements Executable {
    @Override
    public Output execute(Input input) {
        ....
        // find more details in code
    }
}
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
