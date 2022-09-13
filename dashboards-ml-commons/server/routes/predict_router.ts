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
import { PredictService } from '../services/predict_service';
import { PREDICT_API_ENDPOINT } from './constants';

export default function (services: { predictService: PredictService }, router: IRouter) {
    const { predictService } = services;

    router.post(
        {
            path: `${PREDICT_API_ENDPOINT}/{algo}/{modelId}`,
            validate: {
                params: schema.object({
                    algo: schema.string(),
                    modelId: schema.string()
                }),
                body: schema.any()
            }
        },

        async (_context, request) => {
            const {
                modelId,
                algo
            } = request.params;
            try {
                const payload = await predictService.predict({ request, modelId, algo });
                return opensearchDashboardsResponseFactory.ok({ body: payload });
            } catch (err) {
                //Temporarily set error response ok to pass err detail to web
                return opensearchDashboardsResponseFactory.ok({
                    body: {
                        message: err.message
                    }
                });
            }
        }
    );
}
