// https://opensearch.org/docs/latest/ml-commons-plugin/algorithms/
export const SUPPORTED_ALGOS = [
    {
        name: 'K-means',
        value: 'kmeans',
        text: 'K-means',
        parameters: [
            {
                name: 'centroids',
                type: 'integer',
                default: 2

            },
            {
                name: 'iterations',
                type: 'integer',
                default: 10
            },
            {
                name: 'distance_type',
                type: 'enum',
                group: ['EUCLIDEAN', 'COSINE', 'L1'],
                default: 'EUCLIDEAN'
            }
        ]
    }, {
        name: 'Linear regression',
        value: 'LINEAR_REGRESSION',
        text: 'LINEAR_REGRESSION'
    }
]