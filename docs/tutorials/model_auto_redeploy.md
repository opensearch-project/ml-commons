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
There are several configurations to control the model auto redeploy feature behavior including: 
### plugins.ml_commons.model_auto_redeploy.enable
The default value of this configuration is false, value range is: [true, false], once it's set to true, 
it means the model auto redeploy feature is enabled.
### plugins.ml_commons.model_auto_redeploy.lifetime_retry_times
This configuration means how many auto redeploy failure we can tolerate, value range is: [0, Integer.MAX_VALUE],
default value is 3 which means once 3 times of `failure auto redeploy` reached, the system will not retry auto 
redeploy anymore. But once auto redeploy is successful, the retry time will be reset to 0. 
### plugins.ml_commons.model_auto_redeploy_success_ratio
This configuration means how to determine if an auto redeployment is success or not. Since node failure is random, we can't
make sure model auto redeploy can be successful at any time in a cluster, so if most of the expected working nodes have
been successfully redeployed the model, the retry is considered successful. The value range is: [0, 1], and the default 
value of this is 0.8 which means if 80% greater or equals 80% nodes successfully redeployed a model, that model's auto 
redeployment is success.

# Limitation
The auto redeployment of models is designed to handle all cases involving node failures, but it does have its limitations. 
Under the hood, ml-commons uses a cron job to sync up the status of all models in a cluster. 
This cron job checks all the failed nodes and removes them from the internal model routing 
table (a mapping between model ID and operational node IDs) to ensure that requests won't be dispatched to 
crashed nodes.

However, there's one scenario that model auto redeployment cannot handle, which is a complete cluster restart. 
In this situation, if all nodes are shut down, the last live node's cron job will detect that all the models 
are not functioning correctly and update the models' status to DEPLOY_FAILED. 
The model auto redeploy won't check this status because it's not a valid redeployment status. 
In this case, the user will have to invoke the model deploy/load API.

For cases where only some nodes crash, once the plugins.ml_commons.model_auto_redeploy.enable configuration 
is set to true, the models will automatically redeploy on those crashed nodes.

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

