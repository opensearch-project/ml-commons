import { TRAIN_API_ENDPOINT } from '../../server/routes/constants';
import { InnerHttpProvider } from './inner_http_provider';
import { type ALGOS } from '../../common/'

export interface TrainResponse {
    status: string,
    model_id: string
}

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
    public train(algo: ALGOS, params: any, input_data: any) {
        const body = JSON.stringify({
            "parameters": params,
            input_data
        })
        return InnerHttpProvider.getHttp().post<TrainResponse>(TRAIN_API_ENDPOINT, {
            body
        });
    }
}
