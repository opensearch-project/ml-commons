/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


import React, { useCallback, useState } from 'react';
import {
    EuiButton,
    EuiForm,
    EuiFormRow,
    EuiTitle,
    EuiSelect,
    EuiSpacer,
    EuiFieldNumber,
} from '@elastic/eui';
import { SUPPORTED_ALGOS } from '../../../common/algo'
import { APIProvider } from '../../apis/api_provider';
import { ComponentsCommonProps } from '../app'
import { trainSuccessNotification } from '../../utils/notification'

interface Props extends ComponentsCommonProps {

}
export const Train = ({ notifications }: Props) => {
    const [selectedAlgo, setSelectedAlgo] = useState('kmeans')
    const [params, setParams] = useState({ centroids: 2, iterations: 10, distance_type: 'EUCLIDEAN' });
    const [isLoading, setIsLoading] = useState(false);

    const handleBuild = useCallback(async (e) => {
        setIsLoading(true);
        e.preventDefault();
        let result
        try {
            result = await APIProvider.getAPI('train').train()
            const { status, model_id } = result;
            status === "COMPLETED" && trainSuccessNotification(notifications, model_id);
        } catch (e) {
            console.log('err', e)
        }
        setIsLoading(false)
    }, [params, selectedAlgo])
    return (
        <>
            <EuiTitle size="xs">
                <h4>Select a algorithm</h4>
            </EuiTitle>
            <EuiSelect
                onChange={(e) => setSelectedAlgo(e.target.value)}
                value={selectedAlgo}
                options={[
                    {
                        value: 'kmeans',
                        text: 'K-means'
                    }, {
                        value: 'LINEAR_REGRESSION',
                        text: 'LINEAR_REGRESSION'
                    }
                ]}
            />
            <EuiSpacer />
            <EuiTitle size="xs">
                <h4>Parameters</h4>
            </EuiTitle>
            <EuiForm component="form">
                <EuiFormRow label="centroids" helpText="The number of clusters in which to group the generated data">
                    <EuiFieldNumber
                        value={params?.centroids ?? 2}
                        onChange={(e) => setParams({ ...params, centroids: Number(e.target.value) })}
                        aria-label="Use aria labels when no actual label is in use"
                    />
                </EuiFormRow>
                <EuiFormRow label="iterations" helpText="The number of iterations to perform against the data until a mean generates">
                    <EuiFieldNumber
                        value={params?.centroids ?? 2}
                        onChange={(e) => setParams({ ...params, centroids: Number(e.target.value) })}
                        aria-label="Use aria labels when no actual label is in use"
                    />
                </EuiFormRow>
                <EuiFormRow label="distance_type" helpText="The type of measurement from which to measure the distance between centroids">
                    <EuiSelect
                        onChange={(e) => setParams({ ...params, distance_type: e.target.value })}
                        value={params?.distance_type}
                        options={[
                            {
                                value: 'EUCLIDEAN',
                                text: 'EUCLIDEAN'
                            }, {
                                value: 'COSINE',
                                text: 'COSINE'
                            }, {
                                value: 'L1',
                                text: 'L1'
                            }
                        ]}
                    />
                </EuiFormRow>
                {/* <EuiFormRow label="File picker">
                    <EuiFilePicker />
                </EuiFormRow> */}
                <EuiSpacer />
                <EuiButton type="submit" fill onClick={handleBuild} isLoading={isLoading}>
                    Build Model
                </EuiButton>
            </EuiForm>
        </>
    );
};