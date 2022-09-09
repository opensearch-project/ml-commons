import React, { useEffect, useMemo, useState } from 'react';
import { useRouteMatch, Link } from 'react-router-dom';
import {
  EuiPanel,
  EuiPageHeader,
  EuiDescriptionList,
  EuiSpacer,
  EuiLoadingSpinner,
  EuiButton,
} from '@elastic/eui';
import moment from 'moment';

import { APIProvider } from '../../apis/api_provider';
import { ModelDetail as ModelDetailData } from '../../apis/model';
import { routerPaths } from '../../../common/router_paths';

export const ModelDetail = (props: any) => {
  const { params } = useRouteMatch<{ id: string }>();
  const [model, setModel] = useState<ModelDetailData>();

  const modelDescriptionListItems = useMemo(
    () =>
      model
        ? [
            {
              title: 'ID',
              description: model.id,
            },
            {
              title: 'Name',
              description: model.name,
            },
            {
              title: 'Algorithm',
              description: model.algorithm,
            },
            ...(model.trainTime
              ? [
                  {
                    title: 'Train time',
                    description: moment(model.trainTime).format(),
                  },
                ]
              : []),
            {
              title: 'Context',
              description: JSON.stringify(model.context),
            },
            {
              title: 'Content',
              description: <div style={{ wordBreak: 'break-all' }}>{model.content}</div>,
            },
          ]
        : [],
    [model]
  );

  useEffect(() => {
    APIProvider.getAPI('model')
      .getOne(params.id)
      .then((payload) => {
        setModel(payload);
      });
  }, [params.id]);

  if (!model) {
    return null;
  }

  return (
    <EuiPanel>
      <EuiPageHeader
        pageTitle="Model Detail"
        rightSideItems={[
          <Link to={routerPaths.modelList}>
            <EuiButton>Back to list</EuiButton>
          </Link>,
        ]}
        bottomBorder
      />
      <EuiSpacer />

      {model ? (
        <EuiDescriptionList listItems={modelDescriptionListItems} />
      ) : (
        <EuiLoadingSpinner size="xl" />
      )}
    </EuiPanel>
  );
};
