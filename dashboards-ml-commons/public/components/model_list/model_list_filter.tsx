import { EuiFlexItem, EuiFlexGroup } from '@elastic/eui';
import moment from 'moment';
import React from 'react';

import { RangePicker } from '../range_picker';

import { AlgorithmSelector } from './algorithm_selector';
import { ContextSelector } from './context_selector';

export const ModelListFilter = ({
  context,
  onContextChange,
  algorithm,
  onAlgorithmsChange,
  trainedStart,
  onTrainedStartChange,
  trainedEnd,
  onTrainedEndChange,
}: {
  algorithm: string | undefined;
  context: { [key: string]: Array<string | number> } | undefined;
  trainedStart: moment.Moment | null;
  trainedEnd: moment.Moment | null;
  onAlgorithmsChange: (algorithms: string | undefined) => void;
  onContextChange: (context: { [key: string]: Array<string | number> } | undefined) => void;
  onTrainedStartChange: (time: moment.Moment | null) => void;
  onTrainedEndChange: (time: moment.Moment | null) => void;
}) => {
  return (
    <>
      <EuiFlexGroup>
        <EuiFlexItem grow={1}>
          <AlgorithmSelector value={algorithm} onChange={onAlgorithmsChange} fullWidth />
        </EuiFlexItem>
        <EuiFlexItem grow={2}>
          <RangePicker
            start={trainedStart}
            end={trainedEnd}
            onStartChange={onTrainedStartChange}
            onEndChange={onTrainedEndChange}
            placeholders={['Train Start Time', 'Train End Time']}
          />
        </EuiFlexItem>
      </EuiFlexGroup>
      <ContextSelector algorithm={algorithm} value={context} onChange={onContextChange} />
    </>
  );
};
