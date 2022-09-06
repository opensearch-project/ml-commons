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

import {
  ILegacyClusterClient,
  IScopedClusterClient,
  ScopeableRequest,
} from '../../../../../src/core/server';
import { getQueryFromSize, RequestPagination, getPagination } from './utils/pagination';
import { generateTaskSearchQuery, convertTaskSource } from './utils/task';
import { TASK_SEARCH_API } from './utils/constants';

export class TaskNotFound {}

export class TaskService {
  private osClient: ILegacyClusterClient;

  constructor(osClient: ILegacyClusterClient) {
    this.osClient = osClient;
  }

  public async search({
    request,
    pagination,
    modelId,
    functionName,
    createdStart,
    createdEnd,
  }: {
    request: ScopeableRequest;
    modelId?: string;
    functionName?: string;
    createdStart?: number;
    createdEnd?: number;
    pagination: RequestPagination;
  }) {
    const { hits } = await this.osClient
      .asScoped(request)
      .callAsCurrentUser('mlCommonsTask.search', {
        body: {
          query: generateTaskSearchQuery({ modelId, functionName, createdStart, createdEnd }),
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

  public static async getAllFunctions({ client }: { client: IScopedClusterClient }) {
    const {
      body: {
        aggregations: {
          functions: { buckets },
        },
      },
    } = await client.asCurrentUser.transport.request({
      method: 'POST',
      path: TASK_SEARCH_API,
      body: {
        size: 0,
        aggs: {
          functions: {
            terms: {
              field: 'function_name',
            },
          },
        },
      },
    });

    return buckets.map(({ key }: { key: string }) => key);
  }

  public static async getAllStates({ client }: { client: IScopedClusterClient }) {
    const {
      body: {
        aggregations: {
          states: { buckets },
        },
      },
    } = await client.asCurrentUser.transport.request({
      method: 'POST',
      path: TASK_SEARCH_API,
      body: {
        size: 0,
        aggs: {
          states: {
            terms: {
              field: 'state',
            },
          },
        },
      },
    });

    return buckets.map(({ key }: { key: string }) => key);
  }
}
