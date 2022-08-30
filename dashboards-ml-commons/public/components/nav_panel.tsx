import React, { useCallback, useMemo } from 'react';
import { EuiSideNav } from '@elastic/eui';
import { Link, matchPath, useLocation } from 'react-router-dom';

import { ROUTES } from '../../common/router';

export function NavPanel() {
  const location = useLocation();
  const items = useMemo(
    () =>
      ROUTES.map((item) => ({
        id: item.path,
        name: item.label,
        href: item.path,
        isSelected: matchPath(location.pathname, { path: item.path, exact: item.exact }) !== null,
      })),
    [location.pathname]
  );
  const renderItem = useCallback(
    ({ href, ...restProps }) => <Link to={href!} {...restProps} />,
    []
  );
  return <EuiSideNav items={items} renderItem={renderItem} />;
}
