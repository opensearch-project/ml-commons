/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { useEffect, useState } from 'react';
import { IndexPattern } from '../../../../../src/plugins/data/public/index';
import { DataPublicPluginStart } from '../../../../../src/plugins/data/public';


export const useIndexPatterns = (data?: DataPublicPluginStart) => {
    const [indexPatterns, setIndexPatterns] = useState<IndexPattern[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<Error | undefined>(undefined);

    useEffect(() => {
        if (!data) return
        const handleUpdate = async () => {
            try {
                const ids = await data.indexPatterns.getIds(true);
                const patterns = await Promise.all(ids.map((id) => data.indexPatterns.get(id)));
                console.log('patterns', patterns)
                setIndexPatterns(patterns);
            } catch (e) {
                setError(e as Error);
            } finally {
                setLoading(false);
            }
        }
        handleUpdate();
    }, [data]);
    return {
        indexPatterns,
        error,
        loading,
    };
};
