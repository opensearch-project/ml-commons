/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


import React, { useCallback, useState } from 'react';
import {
    EuiButton,
    EuiFormRow,
    EuiTitle,
    EuiSelect,
    EuiSpacer,
    EuiFieldNumber,
    EuiPageHeader,
    EuiFilePicker,
    EuiRadioGroup,
    EuiText
} from '@elastic/eui';
import { SUPPORTED_ALGOS } from '../../../common/algo'
import { APIProvider } from '../../apis/api_provider';
import { ComponentsCommonProps } from '../app'
import { trainSuccessNotification, trainFailNotification } from '../../utils/notification'
import { parseFile, transToInputData } from '../../../public/utils'
import { ParsedResult } from '../data/parse_result';
import { QueryField, type Query } from '../data/query_field';
import { useIndexPatterns } from '../../hooks'
import { type DataSource } from '../../apis/train'
import './index.scss'

import { type ALGOS } from '../../../common/'

interface Props extends ComponentsCommonProps {

}


export const Train = ({ notifications, data }: Props) => {
    const [selectedAlgo, setSelectedAlgo] = useState<ALGOS>('kmeans')
    const [params, setParams] = useState({ centroids: 2, iterations: 10, distance_type: 'EUCLIDEAN' });
    const [isLoading, setIsLoading] = useState(false);
    const [selectedCols, setSelectedCols] = useState<number[]>([])
    const [files, setFiles] = useState([{}])
    const [dataSource, setDataSource] = useState<DataSource>('upload')
    const { indexPatterns } = useIndexPatterns(data);
    const [selectedFields, setSelectedFields] = useState<Record<string, string[]>>({})
    const [query, setQuery] = useState<Query>()


    const [parsedData, setParsedData] = useState({
        data: []
    });

    const onChange = (files) => {
        setFiles(files.length > 0 ? Array.from(files) : []);
        setParsedData({
            data: []
        })
        if (files[0]) {
            parseFile(files[0], (data) => {
                setParsedData(data);
            })
        }

    };

    const renderFiles = () => {
        if (files.length > 0) {
            return (
                <ul>
                    {files.map((file, i) => (
                        <li key={i}>
                            <strong>{file.name}</strong> ({file.size} bytes)
                        </li>
                    ))}
                </ul>
            );
        } else {
            return (
                <p>Add some files to see a demo of retrieving from the FileList</p>
            );
        }
    };


    const handleBuild = useCallback(async (e) => {
        setIsLoading(true);
        e.preventDefault();
        const input_data = transToInputData(parsedData.data, selectedCols);
        let result
        try {
            const body = APIProvider.getAPI('train').convertParams(selectedAlgo, dataSource, params, input_data, { fields: selectedFields, query });
            result = await APIProvider.getAPI('train').train(body)
            const { status, model_id, message } = result;
            if (status === "COMPLETED") {
                trainSuccessNotification(notifications, model_id);
            } else if (message) {
                trainFailNotification(notifications, message)
            }
        } catch (e) {
            console.log('error', e)
        }
        setIsLoading(false)
    }, [params, selectedAlgo, selectedFields, parsedData, selectedCols, dataSource, query])
    return (
        <>
            <EuiPageHeader pageTitle="Train model" bottomBorder />
            <div className='ml-train-form'>
                <EuiTitle size="xs">
                    <h4>Select a algorithm</h4>
                </EuiTitle>
                <EuiSelect
                    onChange={(e) => setSelectedAlgo(e.target.value)}
                    value={selectedAlgo}
                    fullWidth
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
                <EuiFormRow label="centroids" fullWidth helpText="The number of clusters in which to group the generated data">
                    <EuiFieldNumber
                        fullWidth
                        value={params?.centroids ?? 2}
                        onChange={(e) => setParams({ ...params, centroids: Number(e.target.value) })}
                        aria-label="Use aria labels when no actual label is in use"
                    />
                </EuiFormRow>
                <EuiFormRow label="iterations" fullWidth helpText="The number of iterations to perform against the data until a mean generates">
                    <EuiFieldNumber
                        fullWidth
                        value={params?.iterations ?? 10}
                        onChange={(e) => setParams({ ...params, iterations: Number(e.target.value) })}
                        aria-label="Use aria labels when no actual label is in use"
                    />
                </EuiFormRow>
                <EuiFormRow label="distance_type" fullWidth helpText="The type of measurement from which to measure the distance between centroids">
                    <EuiSelect
                        fullWidth
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
                <EuiSpacer />
                <EuiTitle size="xs">
                    <h4>Training Data</h4>
                </EuiTitle>
                <EuiRadioGroup
                    options={[
                        {
                            id: 'upload',
                            label: 'Upload File',
                        },
                        {
                            id: 'query',
                            label: 'Query From OpenSearch'
                        }
                    ]}
                    idSelected={dataSource}
                    onChange={(id) => setDataSource(id)}
                />
                <EuiSpacer />
                {
                    dataSource === 'upload' ? (
                        <>
                            <EuiFormRow label="File picker" fullWidth>
                                <EuiFilePicker
                                    initialPromptText="upload CSV or JSON data"
                                    display='large'
                                    onChange={onChange}
                                    fullWidth
                                />
                            </EuiFormRow>
                            <EuiText>
                                <h5>Files attached</h5>
                                {renderFiles()}
                            </EuiText>
                            {
                                parsedData?.data?.length > 0 ? <ParsedResult data={parsedData.data} selectedCols={selectedCols} onChangeSelectedCols={setSelectedCols} /> : null
                            }
                        </>
                    ) : <QueryField indexPatterns={indexPatterns} selectedFields={selectedFields} onSelectedFields={setSelectedFields} onUpdateQuerys={setQuery} />
                }
                <EuiSpacer />
                <EuiButton color="primary" fill onClick={handleBuild} isLoading={isLoading}>
                    Build Model
                </EuiButton>
            </div>
        </>
    );
};