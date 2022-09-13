import { PREDICT_API_ENDPOINT } from '../../server/routes/constants';
import { InnerHttpProvider } from './inner_http_provider';
import { type Query } from '../../public/components/data/query_field';

type Body = {
    input_query?: Record<string, string | number | Array<string> | Query>,
    input_index?: Array<string>,
}

export class Predict {
    public predict(payload: any, algo: string, modelId: string) {
        const { fields, query } = payload;
        const index = Object.keys(fields)[0];
        if (!index) return {}
        let body: Body = {
            "input_query": {
                "_source": fields[index],
                "size": 10000,
            },
            "input_index": [
                index
            ]
        }
        if (query) {
            body.input_query!.query = query
        }
        return InnerHttpProvider.getHttp().post<any>(`${PREDICT_API_ENDPOINT}/${algo}/${modelId}`, {
            body: JSON.stringify(body)
        });
    }

}
