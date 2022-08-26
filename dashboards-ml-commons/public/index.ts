/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import './index.scss';

import { MlCommonsPluginPlugin } from './plugin';

// This exports static code and TypeScript types,
// as well as, OpenSearch Dashboards Platform `plugin()` initializer.
export function plugin() {
  return new MlCommonsPluginPlugin();
}
export { MlCommonsPluginPluginSetup, MlCommonsPluginPluginStart } from './types';
