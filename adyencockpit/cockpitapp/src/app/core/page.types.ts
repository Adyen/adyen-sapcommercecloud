/** One page of a listing; the same shape as the backend's com.adyen.cockpit.api.page.Page (ADR 0002). */
export interface Page<T> {
  items: T[];
  pageNumber: number;
  pageSize: number;
  totalItems?: number;
  next?: string;
}

/** What a page request may carry; names match the backend's query parameters. */
export interface PageQuery {
  pageNumber?: number;
  pageSize?: number;
  next?: string;
}

/** Query parameters for a paged route: only the fields that are set, `next` passed back unchanged. */
export function pageParams(query: PageQuery): Record<string, string> {
  const params: Record<string, string> = {};
  if (query.pageNumber !== undefined) {
    params['pageNumber'] = String(query.pageNumber);
  }
  if (query.pageSize !== undefined) {
    params['pageSize'] = String(query.pageSize);
  }
  if (query.next) {
    params['next'] = query.next;
  }
  return params;
}
