import { TRAIN_API_ENDPOINT } from '../../server/routes/constants';
import { InnerHttpProvider } from './inner_http_provider';

export interface TrainResponse {
    status: string,
    model_id: string
}

export class Train {
    public train() {
        return InnerHttpProvider.getHttp().post<TrainResponse>(TRAIN_API_ENDPOINT, {
            body: JSON.stringify({
                "parameters": {
                    "centroids": 3,
                    "iterations": 10,
                    "distance_type": "COSINE"
                },
                "input_query": {
                    "_source": ["taxful_total_price", "total_unique_products"],
                    "size": 10000
                },
                "input_index": [
                    "opensearch_dashboards_sample_data_ecommerce"
                ]
            })
        });
    }
}
