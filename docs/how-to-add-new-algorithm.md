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

Create new class `SampleAlgoParams` in `common` package by implements `MLAlgoParams` interface. Must define `NamedXContentRegistry.Entry` in `SampleAlgoParams`, sample code(check more details in ml-commons code)
```
public class SampleAlgoParams implements MLAlgoParams {

    public static final String PARSE_FIELD_NAME = "sample_algo";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );
    
    private static MLAlgoParams parse(XContentParser parser) throws IOException {
        Integer sampleParam = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "sample_param":
                    sampleParam = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new SampleAlgoParams(sampleParam);
    }
    ...
}
```
Register `SampleAlgoParams.XCONTENT_REGISTRY` in `MachineLearningPlugin.java.getNamedXContent()` method
```
    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return ImmutableList
                .of(
                        ...
                        SampleAlgoParams.XCONTENT_REGISTRY
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
```
public class SampleAlgoOutput extends MLOutput{

    public static final String SAMPLE_RESULT_FIELD = "sample_result";
    private Double sampleResult;
    
    @Builder
    public SampleAlgoOutput(Double sampleResult) {
        super(MLOutputType.SAMPLE_ALGO);
        this.sampleResult = sampleResult;
    }

    public SampleAlgoOutput(StreamInput in) throws IOException {
        super(MLOutputType.SAMPLE_ALGO);
        sampleResult = in.readOptionalDouble();
    }
    ...
}
```
Add sample algorithm output type in `MLOutput.fromStream(StreamInput in)` method.
```
    public static MLOutput fromStream(StreamInput in) throws IOException {
        MLOutput output = null;
        MLOutput.MLOutputType outputType = in.readEnum(MLOutput.MLOutputType.class);
        switch (outputType) {
            case TRAINING:
                output = new MLTrainingOutput(in);
                break;
            case PREDICTION:
                output = new MLPredictionOutput(in);
                break;
            
            // Add sample algorithm output type     
            case SAMPLE_ALGO:
                output = new SampleAlgoOutput(in);
                break;
                
            default:
                break;
        }
        return output;
    }
```

### Step 3: add new algorithm in `ml-algorithms` package
Create new class `ml-algorithms/src/main/java/org/opensearch/ml/engine/algorithms/sample/SampleAlgo.java` by implementing interface `MLAlgo`.
Override `train`, `predict` methods.

### Step 4: add new algorithm in `MLEngine` class
Add new algorithm in `MLEngine`, both `train` and `predict` methods.
```
    public static MLOutput predict(MLAlgoName algoName, MLAlgoParams parameters, DataFrame dataFrame, Model model) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        switch (algoName) {
            case KMEANS:
                KMeans kMeans = new KMeans((KMeansParams) parameters);
                return kMeans.predict(dataFrame, model);
            case LINEAR_REGRESSION:
                LinearRegression linearRegression = new LinearRegression((LinearRegressionParams)parameters);
                return linearRegression.predict(dataFrame, model);
            
            // Add sample algorithm
            case SAMPLE_ALGO:
                SampleAlgo sampleAlgo = new SampleAlgo((SampleAlgoParams) parameters);
                return sampleAlgo.predict(dataFrame, model);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }
    
    public static Model train(MLAlgoName algoName, MLAlgoParams parameters, DataFrame dataFrame) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        switch (algoName) {
            case KMEANS:
                KMeans kMeans = new KMeans((KMeansParams) parameters);
                return kMeans.train(dataFrame);
            case LINEAR_REGRESSION:
                LinearRegression linearRegression = new LinearRegression((LinearRegressionParams) parameters);
                return linearRegression.train(dataFrame);
            
            // Add sample algorithm
            case SAMPLE_ALGO:
                SampleAlgo sampleAlgo = new SampleAlgo((SampleAlgoParams) parameters);
                return sampleAlgo.train(dataFrame);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
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
        "sample_param": 22
    },
    "input_data": {
        "column_metas": [
            {
                "name": "total_sum",
                "column_type": "DOUBLE"
            },
            {
                "name": "is_error",
                "column_type": "BOOLEAN"
            }
        ],
        "rows": [
            {
                "values": [
                    {
                        "column_type": "DOUBLE",
                        "value": 15
                    },
                    {
                        "column_type": "BOOLEAN",
                        "value": false
                    }
                ]
            },
            {
                "values": [
                    {
                        "column_type": "DOUBLE",
                        "value": 100
                    },
                    {
                        "column_type": "BOOLEAN",
                        "value": true
                    }
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
        "query": {
            "bool": {
                "filter": [
                    {
                        "range": {
                            "k1": {
                                "gte": 0
                            }
                        }
                    }
                ]
            }
        },
        "size": 10
    },
    "input_index": [
        "predict_data"
    ]
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

### Step 1: add parameter class for new algorithm
Add new enum in `MLAlgoName`
```
public enum MLAlgoName {
    LINEAR_REGRESSION("linear_regression"),
    KMEANS("kmeans"),
    ...
    // Add new sample calculator algorithm name
    LOCAL_SAMPLE_CALCULATOR("local_sample_calculator");
```
Create new class `LocalSampleCalculatorParams` in `common` package by implements `MLAlgoParams` interface. Must define `NamedXContentRegistry.Entry` in it. Check class `LocalSampleCalculatorParams` for details.
Register `LocalSampleCalculatorParams.XCONTENT_REGISTRY` in `MachineLearningPlugin.java.getNamedXContent()` method
```
    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return ImmutableList
            .of(
                KMeansParams.XCONTENT_REGISTRY,
                LinearRegressionParams.XCONTENT_REGISTRY,
                ...
                // Add new sample algirhtm content registry
                LocalSampleCalculatorParams.XCONTENT_REGISTRY
            );
    }
```

### Step 2: add output class for new algorithm
Reuse output class of last example, refer to Step 2 of example 1.

### Step 3: add new algorithm in `ml-algorithms` package
Create new class `ml-algorithms/src/main/java/org/opensearch/ml/engine/algorithms/sample/LocalSampleCalculator.java` by implementing interface `MLAlgo`. Override `execute` method only.

### Step 4: add new algorithm in `MLEngine` class
Add new algorithm in `MLEngine.execute` method.
```
    public static MLOutput execute(MLAlgoName algoName, MLAlgoParams parameters, DataFrame dataFrame) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        switch (algoName) {
            case LOCAL_SAMPLE_CALCULATOR:
                LocalSampleCalculator localSampleAlgo = new LocalSampleCalculator();
                return localSampleAlgo.execute(parameters, dataFrame);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }
```
### Step 5: Run and test
Run `./gradlew run` and test this sample calculator.

```
# Load test data
POST /_bulk
{ "index" : { "_index" : "predict_data" } }
{"k1":0.0,"k2":1.0}
{ "index" : { "_index" : "predict_data" } }
{"k1":12.0,"k2":3.0}

# Run sample calculator
POST _plugins/_ml/_execute/local_sample_calculator
{
    "parameters": {
        "operation": "max"
    },
    "input_query": {
        "query": {
            "bool": {
                "filter": [
                    {
                        "range": {
                            "k1": {
                                "gte": 0
                            }
                        }
                    }
                ]
            }
        },
        "size": 10
    },
    "input_index": [
        "predict_data"
    ]
}

# Sample result: max value of [[0.0, 1.0], [12.0, 3.0]]
{
    "sample_result": 12.0
}

```
