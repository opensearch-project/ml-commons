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
import { ModelService } from '../services';
import { ModelNotFound } from '../services/model_service';
import { MODEL_API_ENDPOINT } from './constants';

export default function (services: { modelService: ModelService }, router: IRouter) {
  const { modelService } = services;

  router.get(
    {
      path: MODEL_API_ENDPOINT,
      validate: {
        query: schema.object({
          algorithms: schema.maybe(
            schema.oneOf([schema.string(), schema.arrayOf(schema.string())])
          ),
          ids: schema.maybe(schema.oneOf([schema.string(), schema.arrayOf(schema.string())])),
          context: schema.maybe(
            schema.string({
              validate: (value) => {
                const errorMessage = 'must be a object stringify json';
                try {
                  const context = JSON.parse(value);
                  if (typeof context !== 'object') {
                    return errorMessage;
                  }
                } catch (err) {
                  return errorMessage;
                }
              },
            })
          ),
          currentPage: schema.number(),
          pageSize: schema.number(),
        }),
      },
    },
    async (_context, request) => {
      const { algorithms, ids, currentPage, pageSize, context: contextInQuery } = request.query;
      try {
        const payload = await modelService.search({
          request,
          algorithms: typeof algorithms === 'string' ? [algorithms] : algorithms,
          ids: typeof ids === 'string' ? [ids] : ids,
          pagination: { currentPage, pageSize },
          context: contextInQuery
            ? ((JSON.parse(contextInQuery) as unknown) as Record<string, Array<string | number>>)
            : undefined,
        });
        return opensearchDashboardsResponseFactory.ok({ body: payload });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.get(
    {
      path: `${MODEL_API_ENDPOINT}/{modelId}`,
      validate: {
        params: schema.object({
          modelId: schema.string(),
        }),
      },
    },
    async (_context, request) => {
      try {
        const model = await modelService.getOne({
          request,
          modelId: request.params.modelId,
        });
        return opensearchDashboardsResponseFactory.ok({ body: model });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.delete(
    {
      path: `${MODEL_API_ENDPOINT}/{modelId}`,
      validate: {
        params: schema.object({
          modelId: schema.string(),
        }),
      },
    },
    async (_context, request) => {
      try {
        await modelService.delete({
          request,
          modelId: request.params.modelId,
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
}
