/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import { Model } from '../public/components/model'
import { Predict } from '../public/components/predict'
import { Home } from '../public/components/home'

export const ROUTES = [
    {
        path: '/model',
        Component: Model,
        label: 'Model',
        icon: 'createSingleMetricJob'
    }, {
        path: '/predict',
        Component: Predict,
        label: 'Prediction',
        icon: 'regressionJob'
    }, {
        path: '/',
        Component: Home,
        exact: true
    }
]