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

import { MappingProperty, PropertyName } from '@opensearch-project/opensearch/api/types';
import {
  ILegacyClusterClient,
  IScopedClusterClient,
  ScopeableRequest,
} from '../../../../../src/core/server';
import { MODEL_BASE_API, MODEL_INDEX, MODEL_SEARCH_API } from './utils/constants';

const getAlgorithmAggsQuery = (properties: Record<PropertyName, MappingProperty>) => {
  const keys = Object.keys(properties);
  if (keys.length === 0) {
    return undefined;
  }
  return {
    size: 0,
    aggs: keys.reduce(
      (pValue, cValue) => ({
        ...pValue,
        [cValue]: {
          terms: {
            field: `model_context.${cValue}${properties[cValue].type === 'text' ? '.keyword' : ''}`,
          },
        },
      }),
      {}
    ),
  };
};

export class ModelAlgorithmService {
  private osClient: ILegacyClusterClient;

  constructor(modelOsClient: ILegacyClusterClient) {
    this.osClient = modelOsClient;
  }

  public async getAll({ request }: { request: ScopeableRequest }) {
    const { aggregations } = await this.osClient
      .asScoped(request)
      .callAsCurrentUser('mlCommonsModel.search', {
        body: {
          size: 0,
          aggs: {
            algorithms: {
              terms: {
                field: 'algorithm',
              },
            },
          },
        },
      });
    return aggregations.algorithms.buckets.map(({ key }: { key: string }) => key);
  }

  public async getOne({ client, algorithm }: { client: IScopedClusterClient; algorithm: string }) {
    const { body } = await client.asCurrentUser.indices.getMapping({
      index: MODEL_INDEX,
    });

    const modelContextProperties =
      body[MODEL_INDEX].mappings.properties?.['model_context']?.properties;
    if (!modelContextProperties) {
      return {};
    }
    const aggQuery = getAlgorithmAggsQuery(modelContextProperties);
    if (!aggQuery) {
      return {};
    }

    const {
      body: { aggregations },
    } = await client.asCurrentUser.transport.request({
      method: 'POST',
      path: MODEL_SEARCH_API,
      body: {
        query: {
          term: {
            algorithm: {
              value: algorithm,
            },
          },
        },
        ...aggQuery,
      },
    });
    if (!aggregations) {
      return {};
    }
    const aggregationKeys = Object.keys(aggregations);

    return {
      filter: aggregationKeys.reduce(
        (pValue, key) => ({
          ...pValue,
          ...(aggregations[key].buckets.length > 0
            ? {
                [key]: aggregations[key].buckets.map((item: { key: number | string }) => item.key),
              }
            : {}),
        }),
        {}
      ),
    };
  }
}
