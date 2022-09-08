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

import { schema } from '@osd/config-schema';
import { IRouter, opensearchDashboardsResponseFactory } from '../../../../../src/core/server';
import { ModelNotFound } from '../services/model_service';
import { TaskService } from '../services/task_service';
import {
  TASK_API_ENDPOINT,
  TASK_FUNCTION_API_ENDPOINT,
  TASK_STATE_API_ENDPOINT,
} from './constants';

export default function (services: { taskService: TaskService }, router: IRouter) {
  const { taskService } = services;

  router.get(
    {
      path: TASK_API_ENDPOINT,
      validate: {
        query: schema.object({
          ids: schema.maybe(schema.oneOf([schema.string(), schema.arrayOf(schema.string())])),
          functionName: schema.maybe(schema.string()),
          modelId: schema.maybe(schema.string()),
          createdStart: schema.maybe(schema.number()),
          createdEnd: schema.maybe(schema.number()),
          currentPage: schema.number(),
          pageSize: schema.number(),
        }),
      },
    },
    async (_context, request) => {
      const { currentPage, pageSize, ids, ...restQueries } = request.query;
      try {
        const payload = await taskService.search({
          request,
          ids: typeof ids === 'string' ? [ids] : ids,
          ...restQueries,
          pagination: { currentPage, pageSize },
        });
        return opensearchDashboardsResponseFactory.ok({ body: payload });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.delete(
    {
      path: `${TASK_API_ENDPOINT}/{taskId}`,
      validate: {
        params: schema.object({
          taskId: schema.string(),
        }),
      },
    },
    async (_context, request) => {
      try {
        await taskService.delete({
          request,
          taskId: request.params.taskId,
        });
        return opensearchDashboardsResponseFactory.ok();
      } catch (err) {
        if (err instanceof ModelNotFound) {
          return opensearchDashboardsResponseFactory.notFound();
        }
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.get(
    {
      path: TASK_FUNCTION_API_ENDPOINT,
      validate: false,
    },
    async (context) => {
      try {
        const body = await TaskService.getAllFunctions({ client: context.core.opensearch.client });
        return opensearchDashboardsResponseFactory.ok({ body });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.get(
    {
      path: TASK_STATE_API_ENDPOINT,
      validate: false,
    },
    async (context) => {
      try {
        const body = await TaskService.getAllStates({ client: context.core.opensearch.client });
        return opensearchDashboardsResponseFactory.ok({ body });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );
}
