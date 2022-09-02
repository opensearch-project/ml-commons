/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import Papa, { ParseResult } from 'papaparse'

type Column_meta = {
    name: string,
    column_type: string
}

type Row = {
    column_type: string
    value: number | string
}

type Rows = { values: Array<Row> }

type Input_data = {
    column_metas: Array<Column_meta>
    rows: Array<Rows>
}

export const parseFile = (file: File, callback: (a: ParseResult<unknown>) => void) => {
    Papa.parse(file, {
        complete: function (results) {
            console.log("Parsing complete:", results);
            callback(results)
        },
        error: function (err) {
            console.error('err', err)
        }
    })
}


export const transToInputData = (data: Array<any>, cols: number[]) => {
    const columns = cols.sort((a, b) => a - b)
    console.log('columns', columns);
    const input_data: Input_data = { column_metas: [], rows: [] }
    input_data.column_metas = [{ name: "d0", column_type: "DOUBLE" }, {
        column_type: "DOUBLE",
        name: "d1"
    }]

    // input_data.rows = data.map((item: any) => {
    //     const res: Rows = { values: [] }
    //     columns.forEach((i: number) => {
    //         const row = {
    //             column_type: "DOUBLE",
    //             value: item[i] ? Number(item[i]) : 0
    //         }
    //         res.values.push(row)
    //     })
    //     return res
    // })
    for (const item of data) {
        const res: Rows = { values: [] }
        columns.forEach((i: number) => {
            if (!item[i]) return
            const row = {
                column_type: "DOUBLE",
                value: Number(item[i])
            }
            res.values.push(row)
        })
        if (res.values.length === columns.length) {
            input_data.rows.push(res)
        }
    }
    return input_data
}