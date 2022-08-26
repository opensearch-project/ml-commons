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

import { ILegacyClusterClient, ScopeableRequest } from '../../../../../src/core/server';
import { getQueryFromSize, RequestPagination, getPagination } from './utils/pagination';

const convertTaskSource = ({
  last_update_time,
  create_time,
  is_async,
  function_name,
  input_type,
  worker_node,
  state,
  model_id,
  task_type,
}: {
  last_update_time: number;
  create_time: number;
  is_async: boolean;
  function_name: string;
  input_type: string;
  worker_node: string;
  state: string;
  model_id: string;
  task_type: string;
}) => ({
  lastUpdateTime: last_update_time,
  createTime: create_time,
  isAsync: is_async,
  functionName: function_name,
  inputType: input_type,
  workerNode: worker_node,
  state,
  modelId: model_id,
  taskType: task_type,
});

export class TaskNotFound {}

export class TaskService {
  private osClient: ILegacyClusterClient;

  constructor(osClient: ILegacyClusterClient) {
    this.osClient = osClient;
  }

  public async search({
    request,
    pagination,
    functionName,
  }: {
    request: ScopeableRequest;
    functionName?: string;
    pagination: RequestPagination;
  }) {
    const { hits } = await this.osClient
      .asScoped(request)
      .callAsCurrentUser('mlCommonsTask.search', {
        body: {
          query:
            functionName === undefined
              ? {
                  match_all: {},
                }
              : {
                  term: {
                    function_name: {
                      value: functionName,
                    },
                  },
                },
          ...getQueryFromSize(pagination),
        },
      });
    return {
      data: hits.hits.map(({ _id, _source }) => ({
        id: _id,
        ...convertTaskSource(_source),
      })),
      pagination: getPagination(pagination.currentPage, pagination.pageSize, hits.total.value),
    };
  }

  public async delete({ request, taskId }: { request: ScopeableRequest; taskId: string }) {
    const { result } = await this.osClient
      .asScoped(request)
      .callAsCurrentUser('mlCommonsTask.delete', {
        taskId,
      });
    if (result === 'not_found') {
      throw new TaskNotFound();
    }
    return true;
  }
}
