/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
    EuiButton,
    EuiForm,
    EuiFormRow,
    EuiSelect,
    EuiSpacer,
    EuiPageHeader
} from '@elastic/eui';
import { ComponentsCommonProps } from '../app'
import { APIProvider } from '../../apis/api_provider';
import { ModelSearchItem } from '../../apis/model';
import { QueryField, type Query } from '../data/query_field';
import { useIndexPatterns } from '../../hooks'
import './index.scss'

interface Props extends ComponentsCommonProps {

}

export const Predict = ({ data }: Props) => {
    const [isLoading, setIsLoading] = useState(false);
    const [models, setModels] = useState<Array<ModelSearchItem & { text: string }>>([]);
    const [selectModel, setSelectModel] = useState('')
    const [selectedFields, setSelectedFields] = useState<Record<string, string[]>>({})
    const { indexPatterns } = useIndexPatterns(data);
    const [query, setQuery] = useState<Query>()

    const handlePredict = useCallback(async () => {
        setIsLoading(true);
        const result = await APIProvider.getAPI('predict')
            .predict({
                query,
                fields: selectedFields
            }, 'kmeans', 'YbZX7YIBiKhA37N5lBVr');
    }, [query, selectedFields])

    useEffect(() => {
        APIProvider.getAPI('model')
            .search({
                currentPage: 1,
                pageSize: 100,
            })
            .then((payload) => {
                const data = payload.data.map(item => ({ ...item, value: item.id, text: `${item.algorithm}_${item.id}` }))
                setModels(data)
            });
    }, []);

    return (
        <>
            <EuiPageHeader pageTitle="Predict" bottomBorder />
            <EuiForm component="form">
                <EuiFormRow label="Select Model" helpText="select a model which used to predict">
                    <EuiSelect
                        options={models}
                        value={selectModel}
                        onChange={(e) => setSelectModel(e.target.value)}
                    />
                </EuiFormRow>
                <EuiSpacer />
                {/* The closing tag is placed here because the EuiBadge embedded in the TopNavMenu component of QueryField causes an automatic refresh issue */}
            </EuiForm>
            <div className='ml-predict-form-below'>
                <QueryField indexPatterns={indexPatterns} selectedFields={selectedFields} onSelectedFields={setSelectedFields} onUpdateQuerys={setQuery} />
                <EuiButton type="submit" fill onClick={handlePredict} isLoading={isLoading}>
                    Predict
                </EuiButton>
            </div>
        </>

    );
}
