import React, { useEffect, useState } from 'react';
import { APIProvider } from '../../apis/api_provider';
import { PrimitiveComboBox } from '../primitive_combo_box';

export const AlgorithmSelector = ({
  value,
  onChange,
}: {
  value: string | undefined;
  onChange: (value: string | undefined) => void;
}) => {
  const [algorithms, setAlgorithms] = useState<string[]>([]);

  useEffect(() => {
    APIProvider.getAPI('modelAlgorithm')
      .getAll()
      .then((algorithms) => {
        setAlgorithms(algorithms);
      });
  }, []);

  return (
    <PrimitiveComboBox
      options={algorithms}
      value={value}
      onChange={onChange}
      placeholder="All algorithm"
    />
  );
};
