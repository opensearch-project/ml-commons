import React, { useMemo, useCallback } from 'react';
import { EuiComboBox, EuiComboBoxProps } from '@elastic/eui';

export type PrimitiveComboBoxProps<T extends string | number> = Omit<
  EuiComboBoxProps<T>,
  'options' | 'selectedOptions' | 'onChange' | 'singleSelection'
> & {
  options: T[];
} & (
    | {
        multi?: false;
        value: T | undefined;
        onChange: (value: T | undefined) => void;
      }
    | {
        multi: true;
        value: T[] | undefined;
        onChange: (value: T[] | undefined) => void;
      }
  );

export const PrimitiveComboBox = <T extends string | number>({
  multi,
  value,
  onChange,
  options: optionsInProps,
  ...restProps
}: PrimitiveComboBoxProps<T>) => {
  const options = useMemo(
    () => optionsInProps.map((option) => ({ label: option.toString(), value: option })),
    [optionsInProps]
  );
  const selectedOptions = useMemo(() => {
    if (multi) {
      return options.filter((option) => value?.includes(option.value));
    }
    return options.filter((option) => value === option.value);
  }, [multi, value, options]);

  const handleChange = useCallback<Required<EuiComboBoxProps<T>>['onChange']>(
    (options) => {
      const result: T[] = [];
      options.forEach((item) => {
        if (item.value !== undefined) {
          result.push(item.value);
        }
      });
      if (multi) {
        onChange(result.length === 0 ? undefined : result);
        return;
      }
      onChange(result[0]);
    },
    [multi, onChange]
  );

  return (
    <EuiComboBox
      options={options}
      selectedOptions={selectedOptions}
      onChange={handleChange}
      {...(multi ? {} : { singleSelection: true })}
      {...restProps}
    />
  );
};
