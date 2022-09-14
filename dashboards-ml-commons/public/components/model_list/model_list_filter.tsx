import { EuiFlexItem, EuiDatePickerRange, EuiDatePicker, EuiFlexGroup } from '@elastic/eui';
import moment from 'moment';
import React, { useCallback } from 'react';

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
  const handleTrainedStartClear = useCallback(() => {
    onTrainedStartChange?.(null);
  }, [onTrainedStartChange]);

  const handleTrainedEndClear = useCallback(() => {
    onTrainedEndChange?.(null);
  }, [onTrainedEndChange]);

  return (
    <>
      <EuiFlexGroup>
        <EuiFlexItem grow={1}>
          <AlgorithmSelector value={algorithm} onChange={onAlgorithmsChange} fullWidth />
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
                onClear={handleTrainedStartClear}
              />
            }
            endDateControl={
              <EuiDatePicker
                aria-label="Train time"
                placeholder="Train End Time"
                showTimeSelect
                selected={trainedEnd}
                onChange={onTrainedEndChange}
                onClear={handleTrainedEndClear}
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
