import { EuiFlexGrid, EuiFlexItem } from '@elastic/eui';
import React from 'react';

import { AlgorithmSelector } from './algorithm_selector';
import { ContextSelector } from './context_selector';

export const ModelListFilter = ({
  context,
  onContextChange,
  algorithm,
  onAlgorithmsChange,
}: {
  algorithm: string | undefined;
  context: { [key: string]: Array<string | number> } | undefined;
  onAlgorithmsChange: (algorithms: string | undefined) => void;
  onContextChange: (context: { [key: string]: Array<string | number> } | undefined) => void;
}) => {
  return (
    <EuiFlexGrid columns={3}>
      <EuiFlexItem>
        <AlgorithmSelector value={algorithm} onChange={onAlgorithmsChange} />
      </EuiFlexItem>
      <ContextSelector algorithm={algorithm} value={context} onChange={onContextChange} />
    </EuiFlexGrid>
  );
};
