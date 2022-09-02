import { EuiFlexGrid, EuiFlexItem } from '@elastic/eui';
import React from 'react';

import { AlgorithmSelector } from './algorithm_selector';
import { ContextSelector } from './context_selector';

export const ModelListFilter = ({
  context,
  onContextChange,
  algorithms,
  onAlgorithmsChange,
}: {
  algorithms: string[] | undefined;
  context: { [key: string]: Array<string | number> } | undefined;
  onAlgorithmsChange: (algorithms: string[] | undefined) => void;
  onContextChange: (context: { [key: string]: Array<string | number> } | undefined) => void;
}) => {
  return (
    <EuiFlexGrid columns={3}>
      <EuiFlexItem>
        <AlgorithmSelector value={algorithms} onChange={onAlgorithmsChange} />
      </EuiFlexItem>
      <ContextSelector algorithm={algorithms?.[0]} value={context} onChange={onContextChange} />
    </EuiFlexGrid>
  );
};
