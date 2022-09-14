import React, { useCallback, useEffect, useRef, useState } from 'react';
import moment from 'moment';
import {
  EuiFieldSearch,
  EuiFlexGrid,
  EuiFlexItem,
  EuiDatePickerRange,
  EuiDatePicker,
  EuiFlexGroup,
} from '@elastic/eui';
import { APIProvider } from '../../apis/api_provider';
import { PrimitiveComboBox } from '../primitive_combo_box';

export interface TaskListFilterValue {
  modelId?: string;
  functionName?: string;
  state?: string;
  createdStart?: moment.Moment | null;
  createdEnd?: moment.Moment | null;
}

export const TaskListFilter = ({
  value,
  onChange,
}: {
  value: TaskListFilterValue;
  onChange: (value: TaskListFilterValue) => void;
}) => {
  const [functions, setFunctions] = useState<string[]>([]);
  const [states, setStates] = useState<string[]>([]);
  const valueRef = useRef(value);
  valueRef.current = value;
  const fireChange = useCallback(
    (update: Partial<TaskListFilterValue>) => {
      onChange({ ...valueRef.current, ...update });
    },
    [onChange]
  );

  const handleModelIdChange = useCallback(
    (e) => {
      fireChange({ modelId: e.target.value });
    },
    [fireChange]
  );

  const handleFunctionChange = useCallback((functionName) => {
    fireChange({ functionName });
  }, []);

  const handleStateChange = useCallback((state) => {
    fireChange({ state });
  }, []);

  const handleCreatedStartChange = useCallback(
    (createdStart) => {
      fireChange({ createdStart });
    },
    [fireChange]
  );

  const handleCreatedEndChange = useCallback(
    (createdEnd) => {
      fireChange({ createdEnd });
    },
    [fireChange]
  );

  const handleCreatedStartClear = useCallback(() => {
    fireChange({ createdStart: null });
  }, [fireChange]);

  const handleCreatedEndClear = useCallback(() => {
    fireChange({ createdEnd: null });
  }, [fireChange]);

  useEffect(() => {
    APIProvider.getAPI('task')
      .getAllFunctions()
      .then((functions) => {
        setFunctions(functions);
      });

    APIProvider.getAPI('task')
      .getAllStates()
      .then((states) => {
        setStates(states);
      });
  }, []);

  return (
    <>
      <EuiFlexGrid columns={3}>
        <EuiFlexItem>
          <EuiFieldSearch
            placeholder="Search by model id"
            value={value.modelId ?? ''}
            onChange={handleModelIdChange}
            fullWidth
          />
        </EuiFlexItem>
        <EuiFlexItem>
          <PrimitiveComboBox
            options={functions}
            value={value.functionName}
            onChange={handleFunctionChange}
            placeholder="All functions"
            fullWidth
          />
        </EuiFlexItem>
        <EuiFlexItem>
          <PrimitiveComboBox
            options={states}
            value={value.state}
            onChange={handleStateChange}
            placeholder="All states"
            fullWidth
          />
        </EuiFlexItem>
      </EuiFlexGrid>
      <EuiFlexGroup>
        <EuiFlexItem>
          <EuiDatePickerRange
            startDateControl={
              <EuiDatePicker
                aria-label="Start time"
                placeholder="Create Start Time"
                selected={value.createdStart}
                onChange={handleCreatedStartChange}
                onClear={handleCreatedStartClear}
                showTimeSelect
              />
            }
            endDateControl={
              <EuiDatePicker
                aria-label="End time"
                placeholder="Create End Time"
                selected={value.createdEnd}
                onChange={handleCreatedEndChange}
                onClear={handleCreatedEndClear}
                showTimeSelect
              />
            }
            fullWidth
          />
        </EuiFlexItem>
      </EuiFlexGroup>
    </>
  );
};
