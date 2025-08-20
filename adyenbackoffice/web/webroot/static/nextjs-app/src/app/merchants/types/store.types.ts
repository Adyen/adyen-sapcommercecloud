export interface StoreAddress {
  city?: string;
  country?: string;
  line1?: string;
  line2?: string;
  line3?: string;
  postalCode?: string;
  stateOrProvince?: string;
}

export interface StoreData {
  id: string;
  address?: StoreAddress;
  description?: string;
  merchantId?: string;
  phoneNumber?: string;
  reference?: string;
  status: string;
  _links?: any;
}

export interface StoreResponse {
  _links?: any;
  itemsTotal?: number;
  pagesTotal?: number;
  data: StoreData[];
}