/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import { Train } from '../public/components/train';
import { Predict } from '../public/components/predict';
import { Home } from '../public/components/home';
import { ModelList } from '../public/components/model_list';
import { TaskList } from '../public/components/task_list';
import { routerPaths } from './router_paths';

export const ROUTES = [
  {
    path: routerPaths.train,
    Component: Train,
    label: 'Train Model',
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
    label: 'Home',
    exact: true,
  },
  {
    path: routerPaths.taskList,
    Component: TaskList,
    label: 'Task List',
    icon: 'createSingleMetricJob',
  },
];
