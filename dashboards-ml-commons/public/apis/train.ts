import { TRAIN_API_ENDPOINT } from '../../server/routes/constants';
import { InnerHttpProvider } from './inner_http_provider';
import { type ALGOS } from '../../common/'
import { type Query } from '../../public/components/data/query_field';

export interface TrainResponse {
    status: string,
    model_id: string,
    message?: string
}
export type DataSource = 'upload' | 'query'

type Body = {
    parameters: Record<string, string>,
    input_query?: Record<string, string | number | Array<string> | Query>,
    input_index?: Array<string>,
    input_data?: Record<string, string>
}

export class Train {
    public train(body: Record<string, string[]>) {
        return InnerHttpProvider.getHttp().post<TrainResponse>(TRAIN_API_ENDPOINT, {
            body: JSON.stringify(body)
        });
    }

    public convertParams(algo: ALGOS, dataSource: DataSource, params: any, input_data: any, { fields, query }: { fields: Record<string, string[]>, query: Query | undefined }): Record<string, any> {

        if (dataSource === 'query') {
            const index = Object.keys(fields)[0];
            if (!index) return {}
            let body: Body = {
                "parameters": params,
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
            return body
        } else {
            return {
                "parameters": params,
                input_data

            }
        }
    }
}