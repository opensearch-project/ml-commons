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
import { ModelService, TrainService } from '../services';
import { ModelNotFound } from '../services/model_service';
import { MODEL_API_ENDPOINT } from './constants';

export default function (
  services: { modelService: ModelService; trainService: TrainService },
  router: IRouter
) {
  const { modelService, trainService } = services;

  router.post(
    {
      path: MODEL_API_ENDPOINT,
      validate: {
        body: schema.object({
          methodName: schema.string(),
          body: schema.any(),
        }),
      },
    },
    async (_context, request) => {
      const { methodName, body } = request.body;
      try {
        const payload = await trainService.trainModel({
          methodName,
          async: true,
          request,
          body,
        });
        return opensearchDashboardsResponseFactory.ok({ body: payload });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.get(
    {
      path: MODEL_API_ENDPOINT,
      validate: {
        query: schema.object({
          algorithm: schema.maybe(schema.string()),
          currentPage: schema.number(),
          pageSize: schema.number(),
        }),
      },
    },
    async (_context, request) => {
      const { algorithm, currentPage, pageSize } = request.query;
      try {
        const payload = await modelService.search({
          request,
          algorithm,
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
