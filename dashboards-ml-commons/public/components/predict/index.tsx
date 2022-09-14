/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
    EuiButton,
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
import { type PredictResponse } from '../../apis/predict'
import { useParams } from "react-router-dom";

interface Props extends ComponentsCommonProps {

}

interface SelectedModel {
    id: string,
    algo: string
}

export const Predict = ({ data }: Props) => {
    const [isLoading, setIsLoading] = useState(false);
    const [modelList, setModelList] = useState<Array<ModelSearchItem & { text: string }>>([]);
    const [selectModel, setSelectModel] = useState<SelectedModel>({ id: '', algo: '' })
    const { id: modelId } = useParams<{ id?: string }>();
    const [selectedFields, setSelectedFields] = useState<Record<string, string[]>>({})
    const { indexPatterns } = useIndexPatterns(data);
    const [query, setQuery] = useState<Query>()

    const handlePredict = useCallback(async () => {
        setIsLoading(true);
        try {
            const result = await APIProvider.getAPI('predict')
                .predict({
                    query,
                    fields: selectedFields
                }, selectModel.algo, selectModel.id) as PredictResponse;
            const { prediction_result, status } = result;
            if (status === 'COMPLETED' && prediction_result) {

            }
        } catch (err) {
            console.error('err', err)
        }
        setIsLoading(false);

    }, [query, selectedFields, selectModel])

    const handleSelectModel = useCallback((id: string) => {
        const model = modelList.find(item => item.id === id);
        if (model) {
            setSelectModel({ id: model.id, algo: model.algorithm })
        }
    }, [modelList])

    useEffect(() => {
        APIProvider.getAPI('model')
            .search({
                currentPage: 1,
                pageSize: 100,
            })
            .then((payload) => {
                const data = payload.data.map(item => ({ ...item, value: item.id, text: `${item.algorithm}_${item.id}` }))
                setModelList(data)
                if (modelId) {
                    const model = data.find(item => item.value === modelId);
                    if (model) {
                        setSelectModel({ id: modelId, algo: model.algorithm })
                        return
                    }
                }
                setSelectModel({ id: data[0].id, algo: data[0].algorithm })
            });
    }, [modelId]);

    return (
        <>
            <EuiPageHeader pageTitle="Predict" bottomBorder />
            <div className='ml-predict-form'>
                <EuiFormRow label="Select Model" fullWidth helpText="select a model which used to predict">
                    <EuiSelect
                        fullWidth
                        options={modelList}
                        value={selectModel.id}
                        onChange={(e) => handleSelectModel(e.target.value)}
                    />
                </EuiFormRow>
                <EuiSpacer />
                <QueryField indexPatterns={indexPatterns} selectedFields={selectedFields} onSelectedFields={setSelectedFields} onUpdateQuerys={setQuery} />
                <EuiButton type="submit" fill onClick={handlePredict} isLoading={isLoading}>
                    Predict
                </EuiButton>
            </div>
        </>

    );
}
