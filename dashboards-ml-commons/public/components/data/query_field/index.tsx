/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


import React, { useEffect, useCallback, useState, useLayoutEffect } from 'react'
import { EuiSelect, EuiTitle, EuiSelectable, EuiSelectableOption, EuiBadge, EuiSpacer } from '@elastic/eui';
import { IndexPattern } from '../../../../../../../src/plugins/data/public/index';
import { useOpenSearchDashboards } from '../../../../../../../src/plugins/opensearch_dashboards_react/public/index';
import { MLServices } from '../../../types'
import { PLUGIN_ID } from '../../../../common'
import { type Filter, buildOpenSearchQuery } from '../../../../../../../src/plugins/data/common'

export type Query = {
    bool?: {
        [key: string]: Array<any>
    }
}

interface Props {
    indexPatterns: IndexPattern[],
    selectedFields: Record<string, string[]>
    onSelectedFields: React.Dispatch<React.SetStateAction<Record<string, string[]>>>
    onUpdateQuerys: React.Dispatch<React.SetStateAction<Query | undefined>>
}

type fieldsOption = Partial<IndexPattern & EuiSelectableOption>
export const QueryField = ({ indexPatterns, selectedFields, onSelectedFields, onUpdateQuerys }: Props) => {
    const [selectedIndex, setSelectedIndex] = useState(0)
    const [fieldsOptions, setFieldsOptions] = useState<Array<fieldsOption>>([]);
    const { services } = useOpenSearchDashboards<MLServices>();
    const {
        setHeaderActionMenu,
        navigation: {
            ui: { TopNavMenu },
        },
        data
    } = services;

    const handleSelectField = useCallback((newOptions: Array<fieldsOption>) => {
        setFieldsOptions(newOptions)
        const fields = newOptions.filter((item) => item?.checked === 'on');
        const selectedFieldsLabels = fields.map(i => i.label);
        const indexTitle = Object.keys(selectedFields)[0];
        onSelectedFields({ [indexTitle]: selectedFieldsLabels as string[] })
    }, [selectedFields, onSelectedFields])

    useEffect(() => {
        setFieldsOptions(indexPatterns[selectedIndex].fields.map(item => ({
            ...item,
            label: item.name,
            prepend: <EuiBadge color="hollow">{item?.type}</EuiBadge>
        })));
        const indexTitle = indexPatterns[selectedIndex].title;
        onSelectedFields({ [indexTitle]: [] });
    }, [selectedIndex, indexPatterns])

    useLayoutEffect(() => {
        const subscription = data.query.state$.subscribe(({ state }) => {
            if (state.filters && state.query) {
                const query = buildOpenSearchQuery(indexPatterns[selectedIndex], state.query, state.filters);
                onUpdateQuerys(query)
            }
        });

        return () => {
            subscription.unsubscribe();
        };
    }, [data.query.state$]);

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
                <h5>Select source fileds</h5>
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

            <EuiSpacer />

            <EuiTitle size="xs">
                <h5>Filter</h5>
            </EuiTitle>
            <TopNavMenu
                appName={PLUGIN_ID}
                setMenuMountPoint={setHeaderActionMenu}
                indexPatterns={[indexPatterns[selectedIndex]]}
                useDefaultBehaviors={true}
                showQueryBar={false}
                showSearchBar={true}
            />
        </>
    )
}