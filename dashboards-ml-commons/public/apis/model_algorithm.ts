import { MODEL_ALGORITHM_API_ENDPOINT } from '../../server/routes/constants';
import { InnerHttpProvider } from './inner_http_provider';

export class ModelAlgorithm {
  public getAll() {
    return InnerHttpProvider.getHttp().get<string[]>(MODEL_ALGORITHM_API_ENDPOINT);
  }

  public getOne(algorithm: string) {
    return InnerHttpProvider.getHttp().get<{ filter: { [key: string]: Array<number | string> } }>(
      `${MODEL_ALGORITHM_API_ENDPOINT}/${algorithm}`
    );
  }
}
