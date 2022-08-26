export interface Pagination {
  currentPage: number;
  pageSize: number;
  totalRecords: number;
  totalPages: number;
}

export type RequestPagination = Pick<Pagination, 'currentPage' | 'pageSize'>;

export const getQueryFromSize = (pagination: RequestPagination) => ({
  from: Math.max(0, pagination.currentPage - 1) * pagination.pageSize,
  size: pagination.pageSize,
});

export const getPagination = (currentPage: number, pageSize: number, totalRecords: number) => ({
  currentPage,
  pageSize,
  totalRecords,
  totalPages: Math.ceil(totalRecords / pageSize),
});
