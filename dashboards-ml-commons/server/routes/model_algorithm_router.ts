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
import { ModelAlgorithmService } from '../services/model_algorithm_service';
import { MODEL_ALGORITHM_API_ENDPOINT } from './constants';

export default function (
  services: { modelAlgorithmService: ModelAlgorithmService },
  router: IRouter
) {
  const { modelAlgorithmService } = services;

  router.get(
    {
      path: MODEL_ALGORITHM_API_ENDPOINT,
      validate: false,
    },
    async (_context, request) => {
      try {
        const payload = await modelAlgorithmService.getAll({
          request,
        });
        return opensearchDashboardsResponseFactory.ok({ body: payload });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );

  router.get(
    {
      path: `${MODEL_ALGORITHM_API_ENDPOINT}/{algorithm}`,
      validate: {
        params: schema.object({
          algorithm: schema.string(),
        }),
      },
    },
    async (context, request) => {
      const { algorithm } = request.params;

      try {
        const body = await modelAlgorithmService.getOne({
          client: context.core.opensearch.client,
          algorithm,
        });
        return opensearchDashboardsResponseFactory.ok({
          body,
        });
      } catch (err) {
        return opensearchDashboardsResponseFactory.badRequest({ body: err.message });
      }
    }
  );
}
