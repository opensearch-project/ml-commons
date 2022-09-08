import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { EuiPageHeader, EuiSpacer, EuiPanel } from '@elastic/eui';

import { TaskSearchItem } from '../../apis/task';
import { CoreStart } from '../../../../../../src/core/public';
import { APIProvider } from '../../apis/api_provider';
import { TaskTable } from './task_table';
import { TaskListFilter, TaskListFilterValue } from './task_list_filter';

export function TaskList({ notifications }: { notifications: CoreStart['notifications'] }) {
  const [tasks, setTasks] = useState<TaskSearchItem[]>([]);
  const [totalTaskCounts, setTotalTaskCounts] = useState<number>();
  const [params, setParams] = useState<
    TaskListFilterValue & {
      currentPage: number;
      pageSize: number;
    }
  >({
    currentPage: 1,
    pageSize: 15,
  });

  const pagination = useMemo(
    () => ({
      currentPage: params.currentPage,
      pageSize: params.pageSize,
      totalRecords: totalTaskCounts,
    }),
    [totalTaskCounts, params.currentPage, params.pageSize]
  );

  const loadByParams = useCallback(
    () =>
      APIProvider.getAPI('task').search({
        ...params,
        createdStart: params.createdStart?.valueOf(),
        createdEnd: params.createdEnd?.valueOf(),
      }),
    [params]
  );

  const handlePaginationChange = useCallback(
    (pagination: { currentPage: number; pageSize: number }) => {
      setParams((previousValue) => {
        if (
          previousValue.currentPage === pagination.currentPage &&
          previousValue.pageSize === pagination.pageSize
        ) {
          return previousValue;
        }
        return {
          ...previousValue,
          ...pagination,
        };
      });
    },
    []
  );

  const handleTaskDeleted = useCallback(async () => {
    const payload = await loadByParams();
    setTasks(payload.data);
    setTotalTaskCounts(payload.pagination.totalRecords);
    notifications.toasts.addSuccess('Task has been deleted.');
  }, [loadByParams]);

  const handleFilterChange = useCallback((filter) => {
    setParams((prevParams) => ({ ...prevParams, ...filter }));
  }, []);

  useEffect(() => {
    loadByParams().then((payload) => {
      setTasks(payload.data);
      setTotalTaskCounts(payload.pagination.totalRecords);
    });
  }, [loadByParams]);

  return (
    <EuiPanel>
      <EuiPageHeader
        pageTitle={
          <>
            Tasks
            {totalTaskCounts !== undefined && (
              <span style={{ fontSize: '0.6em', verticalAlign: 'middle', paddingLeft: 4 }}>
                ({totalTaskCounts})
              </span>
            )}
          </>
        }
        bottomBorder
      />
      <EuiSpacer />
      <TaskListFilter value={params} onChange={handleFilterChange} />
      <EuiSpacer />
      <TaskTable
        tasks={tasks}
        pagination={pagination}
        onTaskDeleted={handleTaskDeleted}
        onPaginationChange={handlePaginationChange}
      />
    </EuiPanel>
  );
}
