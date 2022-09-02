/*
 *   Copyright OpenSearch Contributors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

import { API_ROUTE_PREFIX, MODEL_BASE_API } from '../services/utils/constants';

// eslint-disable-next-line import/no-default-export
export default function (Client: any, config: any, components: any) {
  const ca = components.clientAction.factory;

  if (!Client.prototype.mlCommonsModel) {
    Client.prototype.mlCommonsModel = components.clientAction.namespaceFactory();
  }

  const mlCommonsModel = Client.prototype.mlCommonsModel.prototype;

  mlCommonsModel.search = ca({
    method: 'POST',
    url: {
      fmt: `${MODEL_BASE_API}/_search`,
    },
    needBody: true,
  });

  mlCommonsModel.getOne = ca({
    method: 'GET',
    url: {
      fmt: `${MODEL_BASE_API}/<%=modelId%>`,
      req: {
        modelId: {
          type: 'string',
          required: true,
        },
      },
    },
  });

  mlCommonsModel.delete = ca({
    method: 'DELETE',
    url: {
      fmt: `${MODEL_BASE_API}/<%=modelId%>`,
      req: {
        modelId: {
          type: 'string',
          required: true,
        },
      },
    },
  });

  mlCommonsModel.predict = ca({
    method: 'POST',
    url: {
      fmt: `${API_ROUTE_PREFIX}/_predict/<%=methodName%>/<%=modelId%>`,
      req: {
        methodName: {
          type: 'string',
          required: true,
        },
        modelId: {
          type: 'string',
          required: true,
        },
      },
      needBody: true,
    },
  });
}
