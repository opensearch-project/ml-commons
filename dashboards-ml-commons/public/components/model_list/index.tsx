import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { EuiPageHeader, EuiButton, EuiSpacer, EuiPanel } from '@elastic/eui';
import { Link } from 'react-router-dom';
import moment from 'moment';

import { CoreStart } from '../../../../../../src/core/public';
import { ModelSearchItem } from '../../apis/model';
import { APIProvider } from '../../apis/api_provider';
import { routerPaths } from '../../../common/router_paths';

import { ModelTable } from './model_table';
import { ModelListFilter } from './model_list_filter';
import {
  ModelConfirmDeleteModal,
  ModelConfirmDeleteModalInstance,
} from './model_confirm_delete_modal';

export const ModelList = ({ notifications }: { notifications: CoreStart['notifications'] }) => {
  const confirmModelDeleteRef = useRef<ModelConfirmDeleteModalInstance>(null);
  const [models, setModels] = useState<ModelSearchItem[]>([]);
  const [totalModelCounts, setTotalModelCount] = useState(0);
  const [params, setParams] = useState<{
    algorithms?: string[];
    context?: { [key: string]: Array<string | number> };
    trainedStart?: moment.Moment | null;
    trainedEnd?: moment.Moment | null;
    currentPage: number;
    pageSize: number;
  }>({
    currentPage: 1,
    pageSize: 15,
  });

  const pagination = useMemo(
    () => ({
      currentPage: params.currentPage,
      pageSize: params.pageSize,
      totalRecords: totalModelCounts,
    }),
    [totalModelCounts, params.currentPage, params.pageSize]
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

  const handleModelDeleted = useCallback(async () => {
    const payload = await APIProvider.getAPI('model').search({
      ...params,
      trainedStart: params.trainedStart?.valueOf(),
      trainedEnd: params.trainedEnd?.valueOf(),
    });
    setModels(payload.data);
    setTotalModelCount(payload.pagination.totalRecords);
    notifications.toasts.addSuccess('Model has been deleted.');
  }, [params]);

  const handleAlgorithmsChange = useCallback(
    (algorithms: string | undefined) => {
      setParams((previousValue) => ({
        ...previousValue,
        algorithms: algorithms ? [algorithms] : undefined,
        context: undefined,
      }));
    },
    [setParams]
  );

  const handleContextChange = useCallback((context) => {
    setParams((previousValue) => ({ ...previousValue, context }));
  }, []);

  const handleTrainedStartChange = useCallback((date: moment.Moment | null) => {
    setParams((previousValue) => ({ ...previousValue, trainedStart: date }));
  }, []);

  const handleTrainedEndChange = useCallback((date: moment.Moment | null) => {
    setParams((previousValue) => ({ ...previousValue, trainedEnd: date }));
  }, []);

  const handleModelDelete = useCallback((modelId: string) => {
    confirmModelDeleteRef.current?.show(modelId);
  }, []);

  useEffect(() => {
    APIProvider.getAPI('model')
      .search({
        ...params,
        trainedStart: params.trainedStart?.valueOf(),
        trainedEnd: params.trainedEnd?.valueOf(),
      })
      .then((payload) => {
        setModels(payload.data);
        setTotalModelCount(payload.pagination.totalRecords);
      });
  }, [params]);

  return (
    <EuiPanel>
      <EuiPageHeader
        pageTitle={
          <>
            Models
            {totalModelCounts !== undefined && (
              <span style={{ fontSize: '0.6em', verticalAlign: 'middle', paddingLeft: 4 }}>
                ({totalModelCounts})
              </span>
            )}
          </>
        }
        rightSideItems={[
          <Link to={routerPaths.train}>
            <EuiButton fill>Train new model</EuiButton>
          </Link>,
        ]}
        bottomBorder
      />
      <EuiSpacer />
      <ModelListFilter
        context={params.context}
        algorithm={params.algorithms?.[0]}
        onContextChange={handleContextChange}
        onAlgorithmsChange={handleAlgorithmsChange}
        trainedStart={params.trainedStart}
        trainedEnd={params.trainedEnd}
        onTrainedStartChange={handleTrainedStartChange}
        onTrainedEndChange={handleTrainedEndChange}
      />
      <EuiSpacer />
      <ModelTable
        models={models}
        pagination={pagination}
        onPaginationChange={handlePaginationChange}
        onModelDelete={handleModelDelete}
      />
      <ModelConfirmDeleteModal ref={confirmModelDeleteRef} onDeleted={handleModelDeleted} />
    </EuiPanel>
  );
};
