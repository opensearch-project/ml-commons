/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


import React, { useEffect, useCallback, useState } from 'react'
import { EuiSelect, EuiTitle, EuiSelectable, EuiSelectableOption, EuiSpacer } from '@elastic/eui';
import { IndexPattern } from '../../../../../../src/plugins/data/public/index';


interface Props {
    indexPatterns: IndexPattern[],
    selectedFields: Record<string, string[]>
    onSelectedFields: (val: Record<string, string[]>) => void
}
type fieldsOption = Partial<IndexPattern & EuiSelectableOption>
export const QueryField = ({ indexPatterns, selectedFields, onSelectedFields }: Props) => {
    const [selectedIndex, setSelectedIndex] = useState(0)
    const [fieldsOptions, setFieldsOptions] = useState<Array<fieldsOption>>([]);

    const handleSelectField = useCallback((newOptions: Array<fieldsOption>) => {
        setFieldsOptions(newOptions)
    }, [])

    useEffect(() => {
        setFieldsOptions(indexPatterns[selectedIndex].fields.map(item => ({
            ...item,
            label: item.name
        })))
        onSelectedFields({ '': [] })
    }, [selectedIndex, indexPatterns])
    return (
        <>

            <EuiTitle size="xs">
                <h5>Select index</h5>
            </EuiTitle>
            <EuiSelect
                options={indexPatterns?.map((i, index) => { return { value: index, text: i.title } })}
                value={selectedIndex}
                onChange={(e) => {
                    setSelectedIndex(Number(e.target.value))
                }}
            />
            <EuiSpacer />

            <EuiTitle size="xs">
                <h5>Select two fileds</h5>
            </EuiTitle>
            <EuiSelectable
                aria-label="Searchable example"
                searchable
                searchProps={{
                    'data-test-subj': 'selectableSearchHere',
                }}
                options={fieldsOptions as unknown as EuiSelectableOption[]}
                onChange={handleSelectField}
            >
                {(list, search) => (
                    <>
                        {search}
                        {list}
                    </>
                )}
            </EuiSelectable>
        </>
    )
}