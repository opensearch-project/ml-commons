/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { I18nProvider } from '@osd/i18n/react';
import { Provider as ReduxProvider } from 'react-redux';
import { Route, Switch } from 'react-router-dom';
import { ROUTES } from '../../common/router';

import { EuiPage, EuiPageBody, EuiPageSideBar } from '@elastic/eui';
import store from '../../redux/store';

import { CoreStart, IUiSettingsClient } from '../../../../../src/core/public';
import { NavigationPublicPluginStart } from '../../../../../src/plugins/navigation/public';
import { DataPublicPluginStart } from '../../../../../src/plugins/data/public';

import { NavPanel } from '../components/nav_panel';
import { GlobalBreadcrumbs } from './global_breadcrumbs';
import { useIndexPatterns } from '../hooks'

interface MlCommonsPluginAppDeps {
  basename: string;
  notifications: CoreStart['notifications'];
  http: CoreStart['http'];
  navigation: NavigationPublicPluginStart;
  chrome: CoreStart['chrome'];
  data: DataPublicPluginStart;
  uiSettingsClient: IUiSettingsClient
}

export interface ComponentsCommonProps {
  notifications: CoreStart['notifications'];
  http: CoreStart['http'];
  data: DataPublicPluginStart;
}

export const MlCommonsPluginApp = ({
  basename,
  notifications,
  http,
  chrome,
  data,
}: MlCommonsPluginAppDeps) => {
  useIndexPatterns(data);
  // Render the application DOM.
  // Note that `navigation.ui.TopNavMenu` is a stateful component exported on the `navigation` plugin's start contract.
  return (
    <ReduxProvider store={store}>
      <I18nProvider>
        <Switch>
          <EuiPage restrictWidth="1000px">
            <EuiPageSideBar>
              <NavPanel />
            </EuiPageSideBar>
            <EuiPageBody component="main">
              {ROUTES.map(({ path, Component, exact }) => (
                <Route
                  path={path}
                  render={() => (
                    <Component http={http} notifications={notifications} data={data} />
                  )}
                  exact={exact ?? false}
                />
              ))}
            </EuiPageBody>
          </EuiPage>
        </Switch>
        <GlobalBreadcrumbs chrome={chrome} basename={basename} />
      </I18nProvider>
    </ReduxProvider>
  );
};
