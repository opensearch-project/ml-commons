import React, { useEffect, useState, useCallback, useRef } from 'react';
import { EuiFlexItem } from '@elastic/eui';
import { APIProvider } from '../../apis/api_provider';
import { PrimitiveComboBox } from '../primitive_combo_box';

type FilterOptionValue = string | number;

const ContextSelectorInnter = ({
  identity,
  value,
  options,
  onChange,
}: {
  identity: string;
  value: Array<FilterOptionValue> | undefined;
  options: Array<FilterOptionValue>;
  onChange: (identity: string, value: Array<FilterOptionValue> | undefined) => void;
}) => {
  const handleChange = useCallback(
    (options) => {
      onChange(identity, options);
    },
    [identity, onChange]
  );
  return (
    <PrimitiveComboBox
      options={options}
      value={value}
      multi
      placeholder={`All ${identity}`}
      onChange={handleChange}
    />
  );
};

export const ContextSelector = ({
  algorithm,
  value,
  onChange,
}: {
  algorithm: string | undefined;
  value: { [key: string]: Array<FilterOptionValue> } | undefined;
  onChange: (value: { [key: string]: Array<FilterOptionValue> } | undefined) => void;
}) => {
  const valueRef = useRef(value);
  valueRef.current = value;
  const [filter, setFilter] = useState<{ [key: string]: Array<FilterOptionValue> }>({});
  const keys = Object.keys(filter);

  const handleChange = useCallback(
    (identity: string, value: Array<FilterOptionValue> | undefined) => {
      const newValue = { ...valueRef.current };
      if (value !== undefined) {
        newValue[identity] = value;
      } else {
        delete newValue[identity];
      }
      if (Object.keys(newValue).filter((key) => newValue[key] !== undefined).length === 0) {
        onChange(undefined);
        return;
      }
      onChange(newValue);
    },
    [onChange]
  );

  useEffect(() => {
    setFilter({});
    if (!algorithm) {
      return;
    }
    APIProvider.getAPI('modelAlgorithm')
      .getOne(algorithm)
      .then(({ filter }) => {
        setFilter(filter);
      });
  }, [algorithm]);

  return (
    <>
      {keys.map((key) => (
        <EuiFlexItem key={key}>
          <ContextSelectorInnter
            identity={key}
            value={value?.[key]}
            options={filter?.[key]}
            onChange={handleChange}
          />
        </EuiFlexItem>
      ))}
    </>
  );
};
