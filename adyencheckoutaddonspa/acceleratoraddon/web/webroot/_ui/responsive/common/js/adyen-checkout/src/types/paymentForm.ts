export interface PlaceOrderRequest {
    paymentRequest: any;

    //Billing address related fields
    useAdyenDeliveryAddress?: boolean;
    billingAddress?: AddressData;
    storefrontType: string
    storefrontVersion: string;
}

export interface AddressData {
    addressId: string;
    titleCode: string;
    firstName: string;
    lastName: string;
    line1: string;
    line2: string;
    townCity: string;
    regionIso?: string;
    postcode: string;
    countryIso: string;
    phoneNumber: string;
    companyName: string;
    taxNumber: string;
    registrationNumber: string;
    saveInAddressBook: boolean;
}