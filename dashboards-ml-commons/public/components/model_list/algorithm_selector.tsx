import React, { useEffect, useMemo, useState, useCallback } from 'react';
import { EuiComboBox, EuiComboBoxProps } from '@elastic/eui';
import { APIProvider } from '../../apis/api_provider';

export const AlgorithmSelector = ({
  value,
  onChange,
}: {
  value: string[] | undefined;
  onChange: (value: string[] | undefined) => void;
}) => {
  const [algorithms, setAlgorithms] = useState<string[]>([]);
  const options = useMemo(
    () => algorithms.map((algorithm) => ({ label: algorithm, value: algorithm })),
    [algorithms]
  );
  const selectedOptions = useMemo(() => options.filter((option) => value?.includes(option.value)), [
    value,
    options,
  ]);

  const handleChange = useCallback<Required<EuiComboBoxProps<string>>['onChange']>((options) => {
    const result: string[] = [];
    options.forEach((item) => {
      if (item.value !== undefined) {
        result.push(item.value);
      }
    });
    onChange(result.length === 0 ? undefined : result);
  }, []);

  useEffect(() => {
    APIProvider.getAPI('modelAlgorithm')
      .getAll()
      .then((algorithms) => {
        setAlgorithms(algorithms);
      });
  }, []);

  return (
    <EuiComboBox
      options={options}
      selectedOptions={selectedOptions}
      placeholder="All algorithms"
      singleSelection={{ asPlainText: true }}
      onChange={handleChange}
    />
  );
};
