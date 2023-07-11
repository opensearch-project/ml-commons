# Model Access Control

In the present setting, on a security enabled OpenSearch cluster, admin can limit users to certain actions by mapping them to relevant permissions and permission roles. For example, admin may want some users to only search and view ml models while allow others to register and deploy them. 

Currently, ml-commons has two pre-defined roles to manage permissions. For descriptions of each, see [Permissions](https://opensearch.org/docs/latest/ml-commons-plugin/index/#permissions).
Users can also mix and match individual ml permissions to suit their use case, if these roles don’t meet their needs. Each action corresponds to an operation in the REST API. For example, the `cluster:admin/opensearch/ml/models/delete` permission lets the user delete models.

So far, the ml-commons plugin did not have the concept of ownership. For example, if a user has the `cluster:admin/opensearch/ml/models/deploy` permission, they can deploy all models, regardless of whether who registered them. So, we aim to build a security-based access control so that only certain users have access to models created/owned by other users. That means, even if users have delete model permission, they can delete a resource only if they have access to it which is determied by the access mode of that resource. This limits the resources a user can have access to on the cluster therby reducing the security risks. 

We have implemented backend role-based access in which members of the same back-end roles will be able to access each other’s resources. This is similar to alerting and anomaly detection plugin [backend role based access](https://opensearch.org/docs/latest/observing-your-data/alerting/security/#advanced-limit-access-by-backend-role). Additionally we also let users define an ml resource as public or private. This advanced security access is available only on a cluster with [Security plugin](https://opensearch.org/docs/latest/security/index/).

Note: 
- Model access control is an experimental feature. If you see any bug or have any suggestion, feel free to cut Github issue.



## Setup

For Backend Role based security, it is not enabled by default. To enable/disable it, customers can toggle elasticsearch cluster setting shown below. If this setting is not enabled, a resource can be accessed by anyone.

```
PUT /_cluster/settings
{
  "persistent" : {
    "plugins.ml_commons.model_access_control_enabled" : false 
  }
}
```

For the purpose of this step-by-step tutorial, let us consider admin and four other users with "ml_full_access" permission role i.e. all the users have permissions to perform all actions on ml resources. For more information on users and roles, refer to https://opensearch.org/docs/latest/security/access-control/users-roles/

| Users   | Backend role                      |
|:--------|:----------------------------------|
| `User1` | IT, HR                            |
| `User2` | IT                                |
| `User3` | Finance                           |
| `User4` | -                                 |
| `Admin` | Admin can assign any backend role |



## Model groups

Users can register different versions to the same model. For ease of control, these versions can be registered under a same model group. So, user first needs to create a model group and then start registering model versions to it. A model group can have any number of versions associated with it.

The access control in ml-commons is implemented at the _model group level_ so that all versions of the same model share the same access mode as that of their model group. That is, if user has access to one version, then he/she should have access to all versions of the model group.

When creating a model group, user should also specify the access mode to it. Depending on the access mode, other users will be able to access the versions of a model group or create new versions to it.

If a user tries to register/deploy/undeploy/predict/delete/search/get model versions, first we check if the user has access to the model. Otherwise, we throw exception saying user does not have access to the model specified.

The user who creates a model group will be considered the _model_owner_ of that group and all model versions in it, irrespective of who registers them to the model group. When a model owner creates a model group, the owner can specify one of the following _access modes_ for this model group which will be applied to all its versions:

- `public`: All users who have access to the cluster can access this model group.
- `private`: Only the model owner or an admin user can access this model group.
- `restricted`: The owner, an admin user, or any user who shares one of the model group's backend roles can access any model in this model group. When creating a `restricted` model group, the owner must attach one or more of the owner's backend roles to the model. For a user to be able to access a model group, one of the user's backend roles must match one of the model group's backend roles.

An admin can access all model groups in the cluster regardless of their access mode.



## Registering a model group:

User should use the `_register` API endpoint to register a model group. User can register a model group with a `public`, `private`, or `restricted` access mode.

### Path and HTTP method

```
POST /_plugins/_ml/model_groups/_register
```

### Request fields

The following table lists the available request fields.

| Field                   | Data type | Description                                                                                                                                                                                                                                               |
|:------------------------|:----------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                  | String    | The model group name. Required.                                                                                                                                                                                                                           |
| `description`           | String    | The model group description. Optional.                                                                                                                                                                                                                    |
| `model_access_mode`     | String    | The access mode for this model. Valid values are `public`, `private`, and `restricted`. When this parameter is set to `restricted`, user must specify either `backend_roles` or `add_all_backend_roles`, but not both. Optional. Default is `restricted`. |
| `backend_roles`         | Array     | A list of the model owner's backend roles to add to the model. Required only if the `model_access_mode` is `restricted`. Cannot be specified at the same time as `add_all_backend_roles`. Optional.                                                       |
| `add_all_backend_roles` | Boolean   | If `true`, all backend roles of the model owner are added to the model group. Default is `false`. Cannot be specified at the same time as `backend_roles`. Admin users cannot set this parameter to `true`. Optional.                                     |

### Response fields

The following table lists the available response fields.

| Field            | Data type | Description                                                      |
|:-----------------|:----------|:-----------------------------------------------------------------|
| `model_group_id` | String    | The model group ID that user can use to access this model group. |
| `status`         | String    | The operation status.                                            |

Any user who has appropriate permissions can create a model group on model group index. Lets say User1 wants to create one. User has three options for the model group — he/she can make it public, private, or restricted (attach the back-end roles HR or IT or both).
- To create a public Model Group: User should set `model_access_mode` field to `public`
Any user in the cluster can access this model group and its model versions.

```
POST /_plugins/_ml/model_groups/_register
{
    "name": "test_model_group_public",
    "description": "This is a public model group",
    "model_access_mode": "public"
}
```

```
# Response
{
    "model_group_id": "GDNmQ4gBYW0Qyy5ZcBcg",
    "status": "CREATED"
}
```

- To create a restricted Model Group: User should set `model_access_mode` field to `restricted`

When registering a restricted model group, user must attach one or more of their backend roles to the model group using one but not both of the following ways:
1. Provide a list of backend roles in the `backend_roles` parameter. 
2. Set the `add_all_backend_roles` parameter to `true` to add all the backend roles of the user to the model group. This option is not available to admin users.

Any user who shares one of the model group's backend roles can access any model in this model group.

An admin user can access all model groups regardless of their access mode.


```
//registering model group with backend_roles field

POST /_plugins/_ml/model_groups/_register 
{
    "name": "model_group_test",
    "description": "This is an example description",
    "model_access_mode": "restricted",
    "backend_roles" : ["IT"]
}
```

```
//registering model group with add_all_backend_roles field
POST /_plugins/_ml/model_groups/_register
{
    "name": "model_group_test",
    "description": "This is an example description",
    "model_access_mode": "restricted",
    "add_all_backend_roles": "true"
}
```

- To create a private Model Group: User should set `model_access_mode` to `private`
Only admin or the owner have access to a private model group and its model versions.

```
POST /_plugins/_ml/model_groups/_register
{
    "name": "model_group_test",
    "description": "This is an example description",
    "model_access_mode": "private"
}
```

### Note
- If security or `model_access_control_enabled` setting is disabled in the cluster, every user is null and by default, in such cluster, all model groups are public. User can register a model group with a `name` and `description` but cannot specify any of the access parameters (`model_access_name`, `backend_roles`, or `add_backend_roles`). 

#### Sample request
```
POST /_plugins/_ml/model_groups/_register
{
    "name": "model_group_test",
    "description": "This is an example description"
}
```


## Updating a model group

Updating a model group request is very similar to register model group request. Additionally, user needs to specify the `model_group_id` field in the request to which he/she needs to make an update.


### Path and HTTP method

```
PUT /_plugins/_ml/model_groups/<model_group_id>
```

A user can make updates to a model group to which he/she has access which is determined by the access mode of the model group.

On a _model_access_control_ enabled cluster, the model group owner or an admin user can update all fields. Whereas, any other user who shares one or more backend roles with the model group can update the `name` and `description` fields only.

On a security/model_access_control disabled cluster, any user can make updates to any model group but cannot specify any of the access fields.

For example,
* Consider a model group created by User1 with IT backend_role set to it. Since user1 is the owner, he/she will be able to update all the fields of the model group.
* If User2 tries to send update request to the same model group, only name, and description will be allowed to be updated but not other fields because user2 has access to the model group but is not the owner/admin.
* If User3 tries to send update request, exception will be thrown because user3 has no matching backend_roles of the model group and therefore has no access to it at all.


Sample request allowed by admin/owner

```
PUT /_plugins/_ml/model_groups/<model_group_id>
{
  "name": "model_group_test",
  "description": "This is an example description",
  "add_all_backend_roles": true
}
```

```
#Response
{
  "status": "Updated"
}
```


Sample update request allowed by any other user with access to model group.

```
PUT /_plugins/_ml/model_groups/<model_group_id>
{
    "name": "model_group_test",
    "description": "This is an example description"
}
```

```
#Response
{
  "status": "Updated"
}
```


## Searching for a model group

When user searches for a model group, only those model groups to which user has access will be returned. For example, for a match all query, model groups that will be returned are:

- All public model groups in the index
- Private model groups of the user sending the request, and
- Model groups with atleast one of the backend_roles of the user sending the request.

### Path and HTTP method

```
POST /_plugins/_ml/model_groups/_search
GET /_plugins/_ml/model_groups/_search
```

#### Example request: Match all

The following request is sent by `User1` who has the `IT` and `HR` roles:

```
POST /_plugins/_ml/model_groups/_search
{
  "query": {
    "match_all": {}
  },
  "size": 1000
}
```

#### Example response

```
{
  "took": 31,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 7,
      "relation": "eq"
    },
    "max_score": 1,
    "hits": [
      {
        "_index": ".plugins-ml-model-group",
        "_id": "TRqZfYgBD7s2oEFdvrQj",
        "_version": 1,
        "_seq_no": 2,
        "_primary_term": 1,
        "_score": 1,
        "_source": {
          "backend_roles": [
            "HR",
            "IT"
          ],
          "owner": {
            "backend_roles": [
              "HR",
              "IT"
            ],
            "custom_attribute_names": [],
            "roles": [
              "ml_full_access",
              "own_index",
              "test_ml"
            ],
            "name": "user1",
            "user_requested_tenant": "__user__"
          },
          "created_time": 1685734407714,
          "access": "restricted",
          "latest_version": 0,
          "last_updated_time": 1685734407714,
          "name": "model_group_test",
          "description": "This is an example description"
        }
      },
      {
        "_index": ".plugins-ml-model-group",
        "_id": "URqZfYgBD7s2oEFdyLTm",
        "_version": 1,
        "_seq_no": 3,
        "_primary_term": 1,
        "_score": 1,
        "_source": {
          "backend_roles": [
            "IT"
          ],
          "owner": {
            "backend_roles": [
              "HR",
              "IT"
            ],
            "custom_attribute_names": [],
            "roles": [
              "ml_full_access",
              "own_index",
              "test_ml"
            ],
            "name": "user1",
            "user_requested_tenant": "__user__"
          },
          "created_time": 1685734410470,
          "access": "restricted",
          "latest_version": 0,
          "last_updated_time": 1685734410470,
          "name": "model_group_test",
          "description": "This is an example description"
        }
      },
      ...
    ]
  }
}
```

The following request to search for model groups of a `user` is sent by `user2` who has the `IT` backend role:

```
GET /_plugins/_ml/model_groups/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "nested": {
            "query": {
              "term": {
                "owner.name.keyword": {
                  "value": "user1",
                  "boost": 1
                }
              }
            },
            "path": "owner",
            "ignore_unmapped": false,
            "score_mode": "none",
            "boost": 1
          }
        }
      ]
    }
  }
}
```

#### Example response

```
{
  "took": 6,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 4,
      "relation": "eq"
    },
    "max_score": 0,
    "hits": [
      {
        "_index": ".plugins-ml-model-group",
        "_id": "TRqZfYgBD7s2oEFdvrQj",
        "_version": 1,
        "_seq_no": 2,
        "_primary_term": 1,
        "_score": 0,
        "_source": {
          "backend_roles": [
            "HR",
            "IT"
          ],
          "owner": {
            "backend_roles": [
              "HR",
              "IT"
            ],
            "custom_attribute_names": [],
            "roles": [
              "ml_full_access",
              "own_index",
              "test_ml"
            ],
            "name": "user1",
            "user_requested_tenant": "__user__"
          },
          "created_time": 1685734407714,
          "access": "restricted",
          "latest_version": 0,
          "last_updated_time": 1685734407714,
          "name": "model_group_test",
          "description": "This is an example description"
        }
      },
      ...
    ]
  }
}
```

#### Example request: Search for model groups with a model group ID

```
GET /_plugins/_ml/model_groups/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "terms": {
            "_id": [
              "HyPNK4gBwNxGowI0AtDk"
            ]
          }
        }
      ]
    }
  }
}
```

#### Example response

```
{
  "took": 2,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 1,
      "relation": "eq"
    },
    "max_score": 1,
    "hits": [
      {
        "_index": ".plugins-ml-model-group",
        "_id": "HyPNK4gBwNxGowI0AtDk",
        "_version": 3,
        "_seq_no": 16,
        "_primary_term": 5,
        "_score": 1,
        "_source": {
          "backend_roles": [
            "IT"
          ],
          "owner": {
            "backend_roles": [
              "",
              "HR",
              "IT"
            ],
            "custom_attribute_names": [],
            "roles": [
              "ml_full_access",
              "own_index",
              "test-ml"
            ],
            "name": "user1",
            "user_requested_tenant": null
          },
          "created_time": 1684362035938,
          "latest_version": 2,
          "last_updated_time": 1684362571300,
          "name": "model_group_test",
          "description": "This is an example description"
          }
        }
      }
    ]
  }
}
```



## Deleting a model group

A user can only delete model group if it does not have any model versions in it.

* On a model_access_control enabled cluster, only owner or users with matching backend roles can delete a restricted model group. 
* Users can also delete any public model group if they have delete model_group API permission. 
* For a private model group, only owner or the admin can delete it.
* Admin can delete any model group in the cluster.
* On a security/model_access_control disabled cluster, a user with delete model_group API permission can delete any model group


#### Example request

```
DELETE _plugins/_ml/model_groups/<model_group_id>
```

#### Example response

```
{
  "_index": ".plugins-ml-model-group",
  "_id": "l8nnQogByXnLJ-QNpEk2",
  "_version": 5,
  "result": "deleted",
  "_shards": {
    "total": 2,
    "successful": 1,
    "failed": 0
  },
  "_seq_no": 70,
  "_primary_term": 23
}
```



## Registering a model version to a model group:

Register model version API would have same old path and all the old fields. For information on register version API, please refer: [Registering a model] (https://opensearch.org/docs/latest/ml-commons-plugin/api/#registering-a-model)

Additionally, user should specify the `model_group_id` for registering a new version to a model group. If the model group id is null, an exception will be thrown. 

Everytime a new model is registered to a model group, it is assigned a new version number automatically. Hence, user need not specify the version field while registering a model version.

On a model_access_control enabled cluster:
* To register a new version to a restricted model group, only owner or users with atleast one of their backend roles matching with that of the model group will be allowed.
* For a public model group, any user can register a new version if they have register model permission. 
* For a private model group, only the owner or the admin can register a new version.
* Admin can register model versions to any model group

On a security/model_access_control disabled cluster, 
* a user with register model permission can register a new version to any model group.

For example:
* For a model group created by User1 with IT backend_role, only User1, User2, and admin can register new versions. User3 and User4 cannot register/access versions of this model group
* For a private model group of User1, only User1 or admin will be able to create new versions. For any other user, above exception will be thrown.
* For a public model group of User1, any user can register a version.


#### Example Request: Registering a model version using a pre-trained model

```
POST /_plugins/_ml/models/_register
{
  "name": "huggingface/sentence-transformers/all-MiniLM-L12-v2"
  "model_format": "TORCH_SCRIPT",
  "model_group_id": "FTNlQ4gBYW0Qyy5ZoxfR",
}
```

```
#Response
{
    "task_id": "FjNlQ4gBYW0Qyy5Zxhdh",
    "status": "CREATED"
}
```



## Deploy/Undeploy/Predict/Delete/Get model version

No change to these API requests. User would send the same old requests to perform deploy/undeploy/predict/delete/get actions. For information on these APIs, please refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/

On a model_access_control enabled cluster:
* For the versions in a restricted model group, only owner or users with atleast one of their backend roles matching with that of the model group can perform deploy/undeploy/predict/delete/get actions.
* users can also perform the said actions on any version that belongs to a public model group.
* For a private model version, only its owner or the admin can perform these actions.
* Admin can perform all these actions on any model version in the cluster

On a security/model_access_control disabled cluster,
* a user can perform the actions to which they have permissions to on any model version.




## Search model versions
When a user sends a search model versions request, only those versions which user is having access to will be returned for any query given by the user. 

For eg, for a match all query, model versions that will be returned are those from the
* all public model groups in the index,
* private model groups of the user sending the request, and
* model groups with atleast one of the backend_roles of the user sending the request.

#### Example request: Searching model versions of a model group

```
GET /_plugins/_ml/models/_search
GET /_plugins/_ml/model_groups/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "terms": {
            "model_group_id": [
              "yWvOnIgBKIpucFbpn7v5"
            ]
          }
        }
      ]
    }
  }
}
```

```
# Response
{
  "took": 5,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 2,
      "relation": "eq"
    },
    "max_score": 2,
    "hits": [
      {
        "_index": ".plugins-ml-model",
        "_id": "zmvOnIgBKIpucFbp6bva",
        "_version": 2,
        "_seq_no": 10,
        "_primary_term": 1,
        "_score": 2,
        "_source": {
          "model_version": "1",
          "created_time": 1686257985929,
          "model_format": "TORCH_SCRIPT",
          "description": "test model",
          "model_state": "REGISTERED",
          "total_chunks": 9,
          "model_content_hash_value": "9376c2ebd7c83f99ec2526323786c348d2382e6d86576f750c89ea544d6bbb14",
          "model_config": {
            "all_config": """{"_name_or_path":"nreimers/MiniLM-L6-H384-uncased","architectures":["BertModel"],"attention_probs_dropout_prob":0.1,"gradient_checkpointing":false,"hidden_act":"gelu","hidden_dropout_prob":0.1,"hidden_size":384,"initializer_range":0.02,"intermediate_size":1536,"layer_norm_eps":1e-12,"max_position_embeddings":512,"model_type":"bert","num_attention_heads":12,"num_hidden_layers":6,"pad_token_id":0,"position_embedding_type":"absolute","transformers_version":"4.8.2","type_vocab_size":2,"use_cache":true,"vocab_size":30522}""",
            "model_type": "bert",
            "embedding_dimension": 384,
            "framework_type": "SENTENCE_TRANSFORMERS"
          },
          "last_updated_time": 1686257992344,
          "last_registered_time": 1686257992336,
          "name": "all-MiniLM-L6-v2",
          "model_group_id": "yWvOnIgBKIpucFbpn7v5",
          "model_content_size_in_bytes": 83408741,
          "algorithm": "TEXT_EMBEDDING"
        }
      },
      {
        "_index": ".plugins-ml-model",
        "_id": "2WvPnIgBKIpucFbpnbtt",
        "_version": 2,
        "_seq_no": 21,
        "_primary_term": 1,
        "_score": 2,
        "_source": {
          "model_version": "2",
          "created_time": 1686258031981,
          "model_format": "TORCH_SCRIPT",
          "description": "test model",
          "model_state": "REGISTERED",
          "total_chunks": 9,
          "model_content_hash_value": "9376c2ebd7c83f99ec2526323786c348d2382e6d86576f750c89ea544d6bbb14",
          "model_config": {
            "all_config": """{"_name_or_path":"nreimers/MiniLM-L6-H384-uncased","architectures":["BertModel"],"attention_probs_dropout_prob":0.1,"gradient_checkpointing":false,"hidden_act":"gelu","hidden_dropout_prob":0.1,"hidden_size":384,"initializer_range":0.02,"intermediate_size":1536,"layer_norm_eps":1e-12,"max_position_embeddings":512,"model_type":"bert","num_attention_heads":12,"num_hidden_layers":6,"pad_token_id":0,"position_embedding_type":"absolute","transformers_version":"4.8.2","type_vocab_size":2,"use_cache":true,"vocab_size":30522}""",
            "model_type": "bert",
            "embedding_dimension": 384,
            "framework_type": "SENTENCE_TRANSFORMERS"
          },
          "last_updated_time": 1686258035386,
          "last_registered_time": 1686258035385,
          "name": "all-MiniLM-L6-v2",
          "model_group_id": "yWvOnIgBKIpucFbpn7v5",
          "model_content_size_in_bytes": 83408741,
          "algorithm": "TEXT_EMBEDDING"
        }
      }
    ]
  }
}
```

