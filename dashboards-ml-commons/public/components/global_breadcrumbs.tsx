import { useEffect } from 'react';
import { useLocation, matchPath } from 'react-router-dom';

import { ChromeBreadcrumb, CoreStart } from '../../../../../src/core/public';
import { ROUTES } from '../../common/router';
import { PLUGIN_NAME } from '../../common';

const getBreadcrumbs = (pathname: string, basename: string) => {
  const breadcrumbs: ChromeBreadcrumb[] = [{ text: PLUGIN_NAME, href: basename }];
  const matchedRoute = ROUTES.find((route) =>
    matchPath(pathname, { path: route.path, exact: route.exact })
  );
  if (!matchedRoute?.label) {
    return breadcrumbs;
  }
  return breadcrumbs.concat({
    text: matchedRoute.label,
  });
};

export const GlobalBreadcrumbs = ({
  chrome,
  basename,
}: {
  chrome: CoreStart['chrome'];
  basename: string;
}) => {
  const location = useLocation();

  useEffect(() => {
    chrome.setBreadcrumbs(getBreadcrumbs(location.pathname, basename));
  }, [location.pathname, chrome.setBreadcrumbs, basename]);
  return null;
};
