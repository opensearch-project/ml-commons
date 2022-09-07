import React, { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { EuiFlexItem, EuiFlexGroup, EuiFlexGrid } from '@elastic/eui';
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
  const groupedKeys = useMemo(
    () =>
      Object.keys(filter).reduce<string[][]>((pValue, cValue) => {
        const last = pValue[pValue.length - 1];
        if (!last || last.length === 3) {
          return [...pValue, [cValue]];
        }
        last.push(cValue);
        return pValue;
      }, []),
    [filter]
  );

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
      {groupedKeys.map(([key1, key2, key3]) => (
        <EuiFlexGroup>
          <EuiFlexItem grow={1}>
            <ContextSelectorInnter
              identity={key1}
              value={value?.[key1]}
              options={filter?.[key1]}
              onChange={handleChange}
            />
          </EuiFlexItem>
          <EuiFlexItem grow={2}>
            <EuiFlexGrid columns={2}>
              {key2 && (
                <EuiFlexItem>
                  <ContextSelectorInnter
                    identity={key2}
                    value={value?.[key2]}
                    options={filter?.[key2]}
                    onChange={handleChange}
                  />
                </EuiFlexItem>
              )}
              {key3 && (
                <EuiFlexItem>
                  <ContextSelectorInnter
                    identity={key3}
                    value={value?.[key3]}
                    options={filter?.[key3]}
                    onChange={handleChange}
                  />
                </EuiFlexItem>
              )}
            </EuiFlexGrid>
          </EuiFlexItem>
        </EuiFlexGroup>
      ))}
    </>
  );
};
