
export interface AddressData {
    id: string;
    title: string;
    titleCode: string;
    firstName: string;
    lastName: string;
    companyName: string;
    line1: string;
    line2: string;
    town: string;
    region: RegionData;
    district: string;
    postalCode: string;
    phone: string;
    cellphone: string;
    email: string;
    country: CountryData;
    shippingAddress: boolean;
    billingAddress: boolean;
    defaultAddress: boolean;
    visibleInAddressBook: boolean
    formattedAddress: string
    editable: boolean
    fullname: string
    city: CityData;
    cityDistrict: DistrictData;
    fullnameWithTitle: string
}

export interface RegionData {
    isocode: string;
    isocodeShort: string;
    countryIso: string;
    name: string;
}

interface CountryData {
    isocode: string;
    name: string;
}

interface CityData {
    code: string;
    name: string;
}

interface DistrictData extends CityData {
}