import { TRAIN_API_ENDPOINT } from '../../server/routes/constants';
import { InnerHttpProvider } from './inner_http_provider';
import { type ALGOS } from '../../common/'

export interface TrainResponse {
    status: string,
    model_id: string
}
export type DataSource = 'upload' | 'query'


// {
//     "parameters": {
//         "centroids": 3,
//         "iterations": 10,
//         "distance_type": "COSINE"
//     },
//     "input_query": {
//         "_source": ["taxful_total_price", "total_unique_products"],
//         "size": 10000
//     },
//     "input_index": [
//         "opensearch_dashboards_sample_data_ecommerce"
//     ]
// }

export class Train {
    public train(body: Record<string, string[]>) {
        return InnerHttpProvider.getHttp().post<TrainResponse>(TRAIN_API_ENDPOINT, {
            body: JSON.stringify(body)
        });
    }

    public convertParams(algo: ALGOS, dataSource: DataSource, params: any, input_data: any, selectedFields: Record<string, string[]>): Record<string, any> {

        if (dataSource === 'query') {
            const index = Object.keys(selectedFields)[0];
            if (!index) return {}
            return {
                "parameters": params,
                "input_query": {
                    "_source": selectedFields[index],
                    "size": 10000
                },
                "input_index": [
                    index
                ]
            }

        } else {
            return {
                "parameters": params,
                input_data

            }
        }
    }
}