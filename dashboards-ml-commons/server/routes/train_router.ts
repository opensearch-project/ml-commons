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
import { TrainService } from '../services/train_service';
import { TRAIN_API_ENDPOINT } from './constants';

export default function (services: { trainService: TrainService }, router: IRouter) {
    const { trainService } = services;

    router.post(
        {
            path: TRAIN_API_ENDPOINT,
            validate: {
                body: schema.any()
            }
        },
        async (_context, req) => {
            try {
                const payload = await trainService.trainModel(req);
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
