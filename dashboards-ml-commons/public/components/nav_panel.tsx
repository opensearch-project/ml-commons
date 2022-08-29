import React, { useCallback, useMemo } from 'react';
import { EuiSideNav } from '@elastic/eui';
import { Link, matchPath, useLocation } from 'react-router-dom';

import { routerPaths } from '../../common/router_paths';

const navItems = [
  { id: 'model-list', name: 'Model List', href: routerPaths.modelList },
  { id: 'task-list', name: 'Task List', href: routerPaths.taskList },
];

export function NavPanel() {
  const location = useLocation();
  const items = useMemo(
    () =>
      navItems.map((item) => ({
        ...item,
        isSelected: matchPath(location.pathname, item.href) !== null,
      })),
    [location.pathname]
  );
  const renderItem = useCallback(
    ({ href, ...restProps }) => <Link to={href!} {...restProps} />,
    []
  );
  return <EuiSideNav items={items} renderItem={renderItem} />;
}
