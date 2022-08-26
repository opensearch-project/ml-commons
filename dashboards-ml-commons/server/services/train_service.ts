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

export class TrainService {
  private osClient: ILegacyClusterClient;

  constructor(osClient: ILegacyClusterClient) {
    this.osClient = osClient;
  }

  public async trainModel({
    methodName,
    async,
    body,
    request,
  }: {
    methodName: string;
    async: boolean;
    body: any;
    request: ScopeableRequest;
  }) {
    const response = await this.osClient
      .asScoped(request)
      .callAsCurrentUser('mlCommonsTrain.trainModel', {
        body,
        methodName,
        async,
      });
    if (async) {
      return {
        taskId: response['task_id'],
        status: response['status'],
      };
    }
    return {
      modelId: response['model_id'],
      status: response['status'],
    };
  }
}
