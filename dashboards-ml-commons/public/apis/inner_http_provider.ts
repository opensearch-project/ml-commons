import { CoreStart } from '../../../../../src/core/public';

let httpSotre: CoreStart['http'] | undefined;

export class InnerHttpProvider {
  public static setHttp(http: CoreStart['http'] | undefined) {
    httpSotre = http;
  }

  public static getHttp() {
    return httpSotre!;
  }
}
