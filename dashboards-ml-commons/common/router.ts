/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import { Model } from '../public/components/model';
import { Predict } from '../public/components/predict';
import { Home } from '../public/components/home';
import { ModelList } from '../public/components/model_list';
import { TaskList } from '../public/components/task_list';
import { routerPaths } from './router_paths';

export const ROUTES = [
  {
    path: routerPaths.model,
    component: Model,
    label: 'Model',
    icon: 'createSingleMetricJob',
  },
  {
    path: routerPaths.modelList,
    Component: ModelList,
    label: 'Model List',
    icon: 'createSingleMetricJob',
  },
  {
    path: routerPaths.predict,
    Component: Predict,
    label: 'Prediction',
    icon: 'regressionJob',
  },
  {
    path: '/',
    Component: Home,
    exact: true,
  },
  {
    path: routerPaths.taskList,
    Component: TaskList,
    label: 'Model List',
    icon: 'createSingleMetricJob',
  },
];
