import {AddressData} from "./addressData";

//TODO add pickupOrderGroups appliedvouchers paymentinfo totalDiscounts


interface AbstractOrderData {
    code: string;
    name: string;
    description: string;
    expirationTime: Date;
    net: boolean;
    totalPriceWithTax: PriceData;
    totalPrice: PriceData;
    totalTax: PriceData;
    subTotal: PriceData;
    subTotalWithoutQuoteDiscounts: PriceData;
    deliveryCost: PriceData;
    entries: OrderEntryData[];
    totalItems: number;
    deliveryMode: DeliveryModeData;
    deliveryAddress: AddressData;
    adyenBoletoUrl: string;
    adyenBoletoData: string;
    adyenBoletoBarCodeReference: string;
    adyenBoletoExpirationDate: Date;
    adyenBoletoDueDate: Date;
    adyenMultibancoEntity: string;
    adyenMultibancoAmount: number;
    adyenMultibancoDeadline: string;
    adyenMultibancoReference: string;
    adyenPosReceipt: string;
    subscriptionOrder: boolean;
    calculated: boolean;
    pickupOrderGroups: PickupOrderEntryGroupData;
    totalDiscounts: PriceData;
    appliedProductPromotions: PromotionResultData[]
}

interface PickupOrderEntryGroupData {
    deliveryPointOfService: PointOfServiceData
    distance: number
    entries: OrderEntryData
    totalPriceWithTax: PriceData
    quantity: number
}

interface PointOfServiceData {
    name: string;
    displayName: string;
    address: AddressData;
}

export interface CartData extends AbstractOrderData {

    adyenCseToken: string;
    adyenEncryptedCardNumber: string;
    adyenEncryptedExpiryMonth: string;
    adyenEncryptedExpiryYear: string;
    adyenEncryptedSecurityCode: string;
    adyenCardBrand: string;
    adyenCardType: string;
    adyenCardHolder: string;
    adyenSelectedReference: string;
    adyenRememberTheseDetails: boolean;
    adyenPaymentMethod: string;
    adyenIssuerId: string;
    adyenUPIVirtualAddress: string;
    adyenDob: Date;
    adyenSocialSecurityNumber: string;
    adyenSepaOwnerName: string;
    adyenSepaIbanNumber: string;
    adyenDfValue: string;
    adyenFirstName: string;
    adyenInstallments: number;
    adyenLastName: string;
    adyenReturnUrl: string;
    adyenTerminalId: string;
    adyenBrowserInfo: string;
    adyenBillingAddress: AddressData;
    adyenApplePayMerchantName: string;
    adyenApplePayMerchantIdentifier: string;
    adyenShopperGender: string;
    adyenShopperEmail: string;
    adyenShopperTelephone: string;
    adyenGiftCardBrand: string;
    adyenAmazonPayConfiguration: Record<string, string>;
}

export interface PriceData {

    currencyIso: string;
    value: number;
    formattedValue: string;
}

export interface OrderEntryData {

    entryNumber: number;
    quantity: number;
    basePrice: PriceData;
    totalPrice: PriceData;
    product: ProductData;
    updateable: boolean;
    deliveryMode: DeliveryModeData;
    entries: OrderEntryData[];
    taxValues: TaxValue[];
    deliveryPointOfService: PointOfServiceData
}

interface ProductData {

    code: string;
    name: string;
    url: string;
    description: string;
    manufacturer: string;
    variantType: string;
    price: PriceData;
    baseProduct: string;
    images: ImageData[];

}

export interface ImageData {
    url: string;
    altText: string;
    imageType: "PRIMARY" | "GALLERY" | ""
}

interface TaxValue {
    code: string;
    value: number;
    appliedValue: number;
    absolute: boolean;
    isoCode: string;
}

interface DeliveryModeData {
    code: string;
    name: string;
    description: string;
    deliveryCost: PriceData;
}

export interface PromotionResultData {
    description: string,
    consumedEntries: PromotionOrderEntryConsumedData[]
}

export interface PromotionOrderEntryConsumedData {
    orderEntryNumber: number
}