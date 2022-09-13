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

import { ILegacyClusterClient } from '../../../../../src/core/server';

export class PredictService {
    private osClient: ILegacyClusterClient;

    constructor(osClient: ILegacyClusterClient) {
        this.osClient = osClient;
    }

    public async predict({ request, modelId, algo }) {
        const response = await this.osClient
            .asScoped(request)
            .callAsCurrentUser('mlCommonsPredict.predict', {
                body: request.body,
                modelId,
                algo
            });
        return response
    }
}
