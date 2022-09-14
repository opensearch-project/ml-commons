import React, { useCallback, useMemo } from 'react';
import { EuiSideNav } from '@elastic/eui';
import { generatePath, Link, matchPath, useLocation } from 'react-router-dom';

import { ROUTES } from '../../common/router';

export function NavPanel() {
  const location = useLocation();
  const items = useMemo(
    () =>
      ROUTES.filter((item) => !!item.label).map((item) => {
        const href = generatePath(item.path);
        return {
          id: href,
          name: item.label,
          href,
          isSelected: matchPath(location.pathname, { path: item.path, exact: item.exact }) !== null,
        };
      }),
    [location.pathname]
  );
  const renderItem = useCallback(
    ({ href, ...restProps }) => <Link to={href!} {...restProps} />,
    []
  );
  return <EuiSideNav items={items} renderItem={renderItem} />;
}
