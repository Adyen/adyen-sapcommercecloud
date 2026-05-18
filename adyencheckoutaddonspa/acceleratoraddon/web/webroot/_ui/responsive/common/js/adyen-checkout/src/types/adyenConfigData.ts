import {PaymentMethod} from "@adyen/adyen-web";

export interface AdyenConfigData {
    paymentMethods: PaymentMethod[];
    storedPaymentMethodList: StoredPaymentMethodData[];
    issuerLists: Map<string, string>;
    creditCardLabel: string;
    allowedCards: AllowedCard[];
    amount: AmountData;
    adyenClientKey: string;
    adyenPaypalMerchantId: string;
    deviceFingerPrintUrl: string;
    selectedPaymentMethod: string;
    showRememberTheseDetails: boolean;
    checkoutShopperHost: string;
    environmentMode: string;
    shopperLocale: string;
    openInvoiceMethods: string[];
    showSocialSecurityNumber: boolean;
    showBoleto: boolean;
    showComboCard: boolean;
    showPos: boolean;
    immediateCapture: boolean;
    countryCode: string;
    cardHolderNameRequired: boolean;
    sepaDirectDebit: boolean;
    merchantDisplayName: string,
    shopperEmail: string,
    clickToPayLocale: string,
    installmentOptions?: AdyenInstallmentOptions;
    skipCvcForOneClick: boolean;
}

interface StoredPaymentMethodData {
    brand: string;
    expiryMonth: string;
    expiryYear: string;
    holderName: string;
    iban: string;
    id: string;
    lastFour: string;
    name: string;
    networkTxReference: string;
    ownerName: string;
    shopperEmail: string;
    supportedShopperInteractions: string[];
    type: string;
}

interface AmountData {
    value: number;
    currency: string;
}

interface AllowedCard {
    code: string,
    type: string
}

interface AdyenInstallmentOptions {
    card?: {
        values?: number[];
        plans?: string[];
    };
    showInstallmentAmounts?: {
        values?: number[];
        plans?: string[];
    };
    [key: string]: any;
}