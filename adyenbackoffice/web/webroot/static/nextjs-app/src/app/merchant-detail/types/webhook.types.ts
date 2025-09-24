export interface WebhookData {
  id: string;
  type: string;
  description: string;
  url: string;
  active: boolean;
  communicationFormat: string;
  hasError: boolean;
  includeEventCodes: string[];
}

export interface Link {
  href: string;
}

export interface WebhookLinks {
  self?: Link;
  next?: Link;
  prev?: Link;
  first?: Link;
  last?: Link;
}

export interface WebhookResponse {
  data: WebhookData[];
  _links: WebhookLinks;
}