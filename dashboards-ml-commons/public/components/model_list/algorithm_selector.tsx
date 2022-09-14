import React, { useEffect, useState } from 'react';
import { APIProvider } from '../../apis/api_provider';
import { PrimitiveComboBox, PrimitiveComboBoxProps } from '../primitive_combo_box';

export const AlgorithmSelector = (props: Omit<PrimitiveComboBoxProps<string>, 'options'>) => {
  const [algorithms, setAlgorithms] = useState<string[]>([]);

  useEffect(() => {
    APIProvider.getAPI('modelAlgorithm')
      .getAll()
      .then((algorithms) => {
        setAlgorithms(algorithms);
      });
  }, []);

  return <PrimitiveComboBox<string> {...props} options={algorithms} placeholder="All algorithm" />;
};
