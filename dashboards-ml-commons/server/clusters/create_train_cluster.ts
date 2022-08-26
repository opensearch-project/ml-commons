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

import { CoreSetup } from '../../../../../src/core/server';

import trainPlugin from './train_plugin';
import { CLUSTER } from '../services/utils/constants';

export default function createTrainCluster(core: CoreSetup) {
  return core.opensearch.legacy.createClient(CLUSTER.TRAIN, {
    plugins: [trainPlugin],
  });
}
