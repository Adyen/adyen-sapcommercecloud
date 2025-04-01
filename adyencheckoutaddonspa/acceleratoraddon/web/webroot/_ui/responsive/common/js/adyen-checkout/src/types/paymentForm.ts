export interface PlaceOrderRequest {
    paymentRequest: any;

    //Billing address related fields
    useAdyenDeliveryAddress?: boolean;
    billingAddress?: AddressData;
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
    saveInAddressBook: boolean;
}