# Topic
This doc explains how to use model auto redeploy feature in ml-commons(This doc works for OpenSearch 2.8+).

# Background
After ml-commons support serving model inside OpenSearch cluster, we need to take care the node failure case for the 
deployed models, this can save user's effort to maintain the model's availability. For example, once a node is down
caused by arbitrary reason and then restarted either by script or manually, if there isn't the model auto redeploy 
feature, the model runs on a smaller cluster(expected nodes - 1) which could cause more nodes failure since each working
node is handling more traffic than it expected. To address this we introduced model auto redeploy feature and enabling this
feature is pretty simple.

# Enable model auto redeploy
There's several configurations to control the model auto redeploy feature behavior including: 
### plugins.ml_commons.model_auto_redeploy.enable
The default value of this configuration is false, once it's set to true, it means the model auto redeploy feature is enabled.
### plugins.ml_commons.model_auto_redeploy.lifetime_retry_times
This configuration means how many auto redeploy failure we can tolerate, default value is 3 which means once 3 times of 
`failure auto redeploy` reached, the system will not retry auto redeploy anymore. But once auto redeploy is successful,
the retry time will be reset to 0. 
### plugins.ml_commons.model_auto_redeploy_success_ratio
This configuration means how to determine if an auto redeployment is success or not. Since node failure is random, we can't
make sure model auto redeploy can be successful at any time in a cluster, so if most of the expected working nodes have
been successfully redeployed the model, the retry is considered successful. The default value of this is 0.8 which means
if 80% greater or equals 80% nodes successfully redeployed a model, that model's auto redeployment is success.

# Limitation
Model auto redeploy is to handle all the failure node cases, but it still has limitation. Under the hood, ml-commons use
cron job to sync up all model's status in a cluster, the cron job checks all the failure nodes and remove the failure nodes
in the internal model routing table(this is a mapping between model id and working node ids) to make sure the request won't
be dispatched to crash nodes.
So one case model auto redeploy can't handle is the whole cluster restart, once all nodes are shut down, the last live
node's cron job will detect that all the models are not working correctly and update the model's status to `DEPLOY_FAILED`.
Model auto redeploy won't check this status since this is not a valid redeployment status. In this case, user has to invoke
the [model deploy/load API](https://opensearch.org/docs/latest/ml-commons-plugin/api/#deploying-a-model).
For partial nodes crash case, once the `plugins.ml_commons.model_auto_redeploy.enable` configuration is set to true, the
models will automatically redeploy on those crash nodes.

# Example
An example to enable the model auto redeploy feature is via changing the configuration like below:
```
PUT /_cluster/settings
{
    "persistent" : {
        "plugins.ml_commons.model_auto_redeploy.enable" : true 
  }
}
```
One can also change other two configuration to get desire behavior like below:
```
Changes the life-time retry times to 10:
PUT /_cluster/settings
{
    "persistent" : {
        "plugins.ml_commons.model_auto_redeploy.lifetime_retry_times" : 10 
  }
}

Change the determination of success to 70% expected work nodes in the cluster:
PUT /_cluster/settings
{
    "persistent" : {
        "plugins.ml_commons.model_auto_redeploy.lifetime_retry_times" : 10 
  }
}
```

