import { MODEL_API_ENDPOINT } from '../../server/routes/constants';
import { Pagination } from '../../server/services/utils/pagination';
import { InnerHttpProvider } from './inner_http_provider';

export interface ModelSearchItem {
  id: string;
  name: string;
  algorithm: string;
  context: string;
}

export interface ModelDetail extends ModelSearchItem {
  content: string;
}

export interface ModelSarchResponse {
  data: ModelSearchItem[];
  pagination: Pagination;
}

export class Model {
  public search(query: {
    algorithms?: string[];
    ids?: string[];
    context?: { [key: string]: Array<string | number> };
    currentPage: number;
    pageSize: number;
  }) {
    return InnerHttpProvider.getHttp().get<ModelSarchResponse>(MODEL_API_ENDPOINT, {
      query: {
        ...query,
        context: JSON.stringify(query.context),
      },
    });
  }

  public delete(modelId: string) {
    return InnerHttpProvider.getHttp().delete(`${MODEL_API_ENDPOINT}/${modelId}`);
  }

  public getOne(modelId: string) {
    return InnerHttpProvider.getHttp().get<ModelDetail>(`${MODEL_API_ENDPOINT}/${modelId}`);
  }
}
