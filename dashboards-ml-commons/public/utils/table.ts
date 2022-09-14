import moment from 'moment';

export const DEFAULT_EMPTY_DATA = '-';

export const renderTime = (time: string | number) => {
  const momentTime = moment(time);
  if (time && momentTime.isValid()) return momentTime.format('MM/DD/YY h:mm a');
  return DEFAULT_EMPTY_DATA;
};
