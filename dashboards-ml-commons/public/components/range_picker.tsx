import { EuiDatePicker, EuiDatePickerRange } from '@elastic/eui';
import moment from 'moment';
import React, { useCallback, useRef } from 'react';

export interface RangePickerProps {
  start: moment.Moment | null | undefined;
  end: moment.Moment | null | undefined;
  onStartChange: (date: moment.Moment | null) => void;
  onEndChange: (date: moment.Moment | null) => void;
  placeholders: [string, string];
}

export const RangePicker = ({
  start,
  end,
  onStartChange,
  onEndChange,
  placeholders,
}: RangePickerProps) => {
  const todayRef = useRef(moment().endOf('day'));

  const startFilterDate = useCallback(
    (date: moment.Moment) => {
      const usedEnd = end || todayRef.current;
      return date.isSameOrBefore(usedEnd, 'D');
    },
    [end]
  );

  const endFilterDate = useCallback(
    (date: moment.Moment) => {
      if (start && date.isBefore(start, 'D')) {
        return false;
      }
      if (end && date.isSameOrBefore(end, 'D')) {
        return true;
      }
      return date.isSameOrBefore(todayRef.current, 'D');
    },
    [start, end]
  );

  const handleStartClear = useCallback(() => {
    onStartChange(null);
  }, []);

  const handleEndClear = useCallback(() => {
    onEndChange(null);
  }, [onEndChange]);

  return (
    <EuiDatePickerRange
      startDateControl={
        <EuiDatePicker
          placeholder={placeholders[0]}
          showTimeSelect
          selected={start}
          onChange={onStartChange}
          onClear={handleStartClear}
          startDate={start}
          endDate={end}
          filterDate={startFilterDate}
        />
      }
      endDateControl={
        <EuiDatePicker
          placeholder={placeholders[1]}
          showTimeSelect
          selected={end}
          onChange={onEndChange}
          onClear={handleEndClear}
          startDate={start}
          endDate={end}
          filterDate={endFilterDate}
        />
      }
      fullWidth
    />
  );
};
