/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback } from 'react';
import {
    EuiButton,
    EuiForm,
    EuiFormRow,
    EuiSelect,
    EuiSpacer,
    EuiPageHeader
} from '@elastic/eui';
import { ComponentsCommonProps } from '../app'
interface Props extends ComponentsCommonProps {

}

export const Predict = ({ }: Props) => {
    const [isLoading, setIsLoading] = useState(false);
    const [selectModel, setSelectModel] = useState('');
    const [selectData, setSelectData] = useState('')
    const handlePredict = useCallback(() => {

    }, [])

    return (
        <>
            <EuiPageHeader pageTitle="Predict" bottomBorder />
            <EuiForm component="form">
                <EuiFormRow label="Select Model" helpText="select a model which used to predict">
                    <EuiSelect
                        options={[]}
                        value={selectModel}
                        onChange={(e) => setSelectModel(e.target.value)}
                    />
                </EuiFormRow>
                <EuiFormRow label="Select Frame" helpText="Select a frame data">
                    <EuiSelect
                        onChange={(e) => setSelectData(e.target.value)}
                        value={selectData}
                        options={[
                        ]}
                    />
                </EuiFormRow>
                <EuiSpacer />
                <EuiButton type="submit" fill onClick={handlePredict} isLoading={isLoading}>
                    Predict
                </EuiButton>
            </EuiForm>
        </>

    );
}
