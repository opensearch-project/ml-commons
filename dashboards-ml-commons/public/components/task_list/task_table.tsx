import React, { useMemo, useCallback, useRef } from 'react';
import { EuiBasicTable } from '@elastic/eui';

import { TaskSearchItem } from '../../apis/task';
import { TaskDeleteButton } from './task_delete_button';
import { renderTime } from '../../utils';

export function TaskTable(props: {
  tasks: TaskSearchItem[];
  pagination: {
    currentPage: number;
    pageSize: number;
    totalRecords: number | undefined;
  };
  onPaginationChange: (pagination: { currentPage: number; pageSize: number }) => void;
  onTaskDeleted: () => void;
}) {
  const { tasks, onPaginationChange, onTaskDeleted } = props;
  const onPaginationChangeRef = useRef(onPaginationChange);
  onPaginationChangeRef.current = onPaginationChange;
  const onTaskDeletedRef = useRef(onTaskDeleted);
  onTaskDeletedRef.current = onTaskDeleted;

  const columns = useMemo(
    () => [
      {
        field: 'id',
        name: 'ID',
      },
      {
        field: 'functionName',
        name: 'Function Name',
      },
      {
        field: 'modelId',
        name: 'Model ID',
      },
      {
        field: 'createTime',
        name: 'Create Time',
        render: renderTime,
      },
      {
        field: 'lastUpdateTime',
        name: 'Last Update Time',
        render: renderTime,
      },
      {
        field: 'isAsync',
        name: 'Async',
      },
      {
        field: 'state',
        name: 'State',
      },
      {
        name: 'Actions',
        field: 'id',
        width: '130px',
        render: (id: string) => <TaskDeleteButton id={id} onDeleted={onTaskDeleted} />,
      },
    ],
    [onTaskDeleted]
  );

  const pagination = useMemo(
    () => ({
      pageIndex: props.pagination.currentPage - 1,
      pageSize: props.pagination.pageSize,
      totalItemCount: props.pagination.totalRecords || 0,
      pageSizeOptions: [15, 30, 50, 100],
      showPerPageOptions: true,
    }),
    [props.pagination]
  );

  const handleChange = useCallback(
    ({ page }) => {
      if (page) {
        onPaginationChangeRef.current({ currentPage: page.index + 1, pageSize: page.size });
      }
    },
    [onPaginationChangeRef.current]
  );

  return (
    <EuiBasicTable<TaskSearchItem>
      columns={columns}
      items={tasks}
      pagination={pagination}
      onChange={handleChange}
    />
  );
}
