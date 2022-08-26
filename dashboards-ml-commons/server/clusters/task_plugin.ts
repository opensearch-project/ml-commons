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

import { TASK_BASE_API } from '../services/utils/constants';

// eslint-disable-next-line import/no-default-export
export default function (Client: any, config: any, components: any) {
  const ca = components.clientAction.factory;

  if (!Client.prototype.mlCommonsTask) {
    Client.prototype.mlCommonsTask = components.clientAction.namespaceFactory();
  }

  const mlCommonsTask = Client.prototype.mlCommonsTask.prototype;

  mlCommonsTask.search = ca({
    method: 'POST',
    url: {
      fmt: `${TASK_BASE_API}/_search`,
    },
    needBody: true,
  });

  mlCommonsTask.getById = ca({
    method: 'GET',
    url: {
      fmt: `${TASK_BASE_API}/<%=taskId%>`,
      req: {
        taskId: {
          type: 'string',
          required: true,
        },
      },
    },
  });

  mlCommonsTask.delete = ca({
    method: 'DELETE',
    url: {
      fmt: `${TASK_BASE_API}/<%=taskId%>`,
      req: {
        taskId: {
          type: 'string',
          required: true,
        },
      },
    },
  });
}
