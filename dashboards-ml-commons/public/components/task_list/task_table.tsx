import React, { useMemo, useCallback, useRef } from 'react';
import { EuiBasicTable } from '@elastic/eui';
import moment from 'moment';
import { DefaultItemIconButtonAction } from '@elastic/eui/src/components/basic_table/action_types';

import { APIProvider } from '../../apis/api_provider';
import { TaskSearchItem } from '../../apis/task';

const renderDateTime = (value: number) => moment(value).format();

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

  const handleTaskDelete = useCallback((item: TaskSearchItem) => {
    APIProvider.getAPI('task')
      .delete(item.id)
      .then(() => {
        onTaskDeletedRef.current();
      });
  }, []);

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
        render: renderDateTime,
      },
      {
        field: 'lastUpdateTime',
        name: 'Last Update Time',
        render: renderDateTime,
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
        actions: [
          {
            name: 'Delete',
            description: 'Delete this task',
            icon: 'trash',
            type: 'icon',
            color: 'danger',
            onClick: handleTaskDelete,
          } as DefaultItemIconButtonAction<TaskSearchItem>,
        ],
      },
    ],
    [handleTaskDelete]
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
    <EuiBasicTable
      columns={columns}
      items={tasks}
      pagination={pagination}
      onChange={handleChange}
    />
  );
}
