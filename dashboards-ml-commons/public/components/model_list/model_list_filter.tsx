import { EuiFlexItem, EuiDatePickerRange, EuiDatePicker, EuiFlexGroup } from '@elastic/eui';
import moment from 'moment';
import React from 'react';

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
  trainedStart?: moment.Moment | null;
  trainedEnd?: moment.Moment | null;
  onAlgorithmsChange: (algorithms: string | undefined) => void;
  onContextChange: (context: { [key: string]: Array<string | number> } | undefined) => void;
  onTrainedStartChange?: (time: moment.Moment | null) => void;
  onTrainedEndChange?: (time: moment.Moment | null) => void;
}) => {
  return (
    <>
      <EuiFlexGroup>
        <EuiFlexItem grow={1}>
          <AlgorithmSelector value={algorithm} onChange={onAlgorithmsChange} />
        </EuiFlexItem>
        <EuiFlexItem grow={2}>
          <EuiDatePickerRange
            startDateControl={
              <EuiDatePicker
                aria-label="Train time"
                placeholder="Train Start Time"
                showTimeSelect
                selected={trainedStart}
                onChange={onTrainedStartChange}
              />
            }
            endDateControl={
              <EuiDatePicker
                aria-label="Train time"
                placeholder="Train End Time"
                showTimeSelect
                selected={trainedEnd}
                onChange={onTrainedEndChange}
              />
            }
            fullWidth
          />
        </EuiFlexItem>
      </EuiFlexGroup>
      <ContextSelector algorithm={algorithm} value={context} onChange={onContextChange} />
    </>
  );
};
