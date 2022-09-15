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
import { RangePicker } from '../range_picker';

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
          <RangePicker
            start={value.createdStart}
            end={value.createdEnd}
            onStartChange={handleCreatedStartChange}
            onEndChange={handleCreatedEndChange}
            placeholders={['Create Start Time', 'Create End Time']}
          />
        </EuiFlexItem>
      </EuiFlexGroup>
    </>
  );
};
