/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


import React, { useCallback } from 'react'
import './index.scss'
import {
    EuiCheckbox, EuiText, EuiSpacer
} from '@elastic/eui'

type Props = {
    data: any
    selectedCols: number[],
    onChangeSelectedCols: (val: number[]) => void
}

export const ParsedResult = ({ data, selectedCols, onChangeSelectedCols }: Props) => {
    const sliceLen = data.length >= 7 ? 7 : data.length;
    const slicedData = data.slice(0, sliceLen);
    const keysLen = Object.keys(data[0]).length
    const loopArr = new Array(keysLen).fill(0);
    const handleChecked = useCallback((checked, value: number) => {
        if (checked) {
            onChangeSelectedCols([...selectedCols, value])
        } else {
            const index = selectedCols.indexOf(value);
            const arr = [...selectedCols];
            arr.splice(index, 1);
            onChangeSelectedCols(arr);
        }
    }, [selectedCols])
    return (
        <>
            <EuiSpacer />
            <EuiText>
                <h4>Parsed Result</h4>
            </EuiText>
            <table className="ml-train-parsed-table" style={{ width: '100%' }}>
                <tbody>
                    {loopArr.map((_, index) => (
                        <tr key={index}>
                            <EuiCheckbox id={String(index)} checked={selectedCols.indexOf(index) > -1} onChange={(e) => handleChecked(e.target.checked, index)} />
                            <td>Col {index + 1} </td>
                            {slicedData.map((item, i: number) => (
                                <td key={i}>{item[index]}</td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </>
    )
}
