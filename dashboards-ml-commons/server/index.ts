import { PluginInitializerContext } from '../../../../src/core/server';
import { MlCommonsPlugin } from './plugin';

// This exports static code and TypeScript types,
// as well as, OpenSearch Dashboards Platform `plugin()` initializer.

export function plugin(initializerContext: PluginInitializerContext) {
  return new MlCommonsPlugin(initializerContext);
}

export { MlCommonsPluginSetup, MlCommonsPluginStart } from './types';
