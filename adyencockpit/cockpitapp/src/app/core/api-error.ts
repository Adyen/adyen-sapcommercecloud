/** The body of every error response of the cockpit API (ADR 0002). */
export interface ApiError {
  code: string;
  message: string;
  field?: string;
  details?: Record<string, unknown>;
  correlationId: string;
}

/** Control object of a dangerous route's request body (ADR 0002). */
export interface ActionConfirmation {
  reason: string;
  storeName?: string;
}

/** `details` of a 409 CONCURRENT_MODIFICATION error (ADR 0002). */
export interface ConcurrentModificationDetails {
  modifiedBy: string;
  modifiedAt: string;
}
