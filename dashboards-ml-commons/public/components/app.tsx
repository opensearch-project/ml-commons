/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { I18nProvider } from '@osd/i18n/react';
import { Provider } from 'react-redux';
import { BrowserRouter as Router, Route, Switch } from 'react-router-dom';
import { ROUTES } from '../../common/router'

import {
  EuiPage,
  EuiPageBody,
} from '@elastic/eui';
import store from '../../redux/store';

import { CoreStart } from '../../../../src/core/public';
import { NavigationPublicPluginStart } from '../../../../src/plugins/navigation/public';

import { PLUGIN_ID } from '../../common';

interface MlCommonsPluginAppDeps {
  basename: string;
  notifications: CoreStart['notifications'];
  http: CoreStart['http'];
  navigation: NavigationPublicPluginStart;
}

export const MlCommonsPluginApp = ({
  basename,
  notifications,
  http,
  navigation,
}: MlCommonsPluginAppDeps) => {
  // Render the application DOM.
  // Note that `navigation.ui.TopNavMenu` is a stateful component exported on the `navigation` plugin's start contract.
  return (
    <Provider store={store}>
      <Router basename={basename}>
        <I18nProvider>
          <Switch>
            <>
              <navigation.ui.TopNavMenu
                appName={PLUGIN_ID}
                showSearchBar={true}
                useDefaultBehaviors={true}
              />
              <EuiPage restrictWidth="1000px">
                <EuiPageBody component="main">
                  {ROUTES.map(({ path, Component, exact }) => (
                    <Route path={path} render={Component} exact={exact ?? false} />
                  ))}
                </EuiPageBody>
              </EuiPage>
            </>
          </Switch>
        </I18nProvider>
      </Router>
    </Provider>
  );
};
