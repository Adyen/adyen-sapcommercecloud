const BASE = '/adyenbackoffice/api/setup';

const SESSION_LOST = 'Your session has expired. Reload the page and sign in again.';

export interface ApiResult<T> {
  status: number;
  body: T;
}

/**
 * A refused credential and a partial provisioning run both arrive as 422 carrying a body worth
 * showing, so the status is returned rather than thrown. Only answers with nothing to render throw.
 */
async function parse<T>(response: Response): Promise<ApiResult<T>> {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return { status: response.status, body: (await response.json()) as T };
  }
  if (response.status === 400) {
    throw new Error('The server rejected the request as incomplete. Check that every field is filled in.');
  }
  if (!response.ok && response.status !== 401 && response.status !== 403) {
    throw new Error(`The server answered ${response.status}.`);
  }
  // Spring Security answers an unauthenticated call with the login page, not with JSON.
  throw new Error(SESSION_LOST);
}

export async function getJson<T>(path: string): Promise<ApiResult<T>> {
  const response = await fetch(BASE + path, { headers: { Accept: 'application/json' } });
  return parse<T>(response);
}

export async function postJson<T>(path: string, payload: unknown): Promise<ApiResult<T>> {
  const response = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload),
  });
  return parse<T>(response);
}
