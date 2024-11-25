import {AdyenConfigData} from "../types/adyenConfigData";
import {PayloadAction, RootAction} from "./rootReducer";

export const adyenConfigInitialState : AdyenConfigData = {
    cardHolderNameRequired: false,
    checkoutShopperHost: "",
    countryCode: "",
    environmentMode: "",
    immediateCapture: false,
    openInvoiceMethods: [],
    selectedPaymentMethod: "",
    sessionData: undefined,
    shopperLocale: "",
    showBoleto: false,
    showComboCard: false,
    showPos: false,
    showRememberTheseDetails: false,
    showSocialSecurityNumber: false,
    adyenClientKey: "",
    adyenPaypalMerchantId: "",
    allowedCards: [],
    paymentMethods: [],
    amount: undefined,
    connectedTerminalList: [],
    creditCardLabel: "",
    deviceFingerPrintUrl: "",
    issuerLists: undefined,
    sepaDirectDebit: undefined,
    storedPaymentMethodList: [],
    merchantDisplayName: "",
    shopperEmail: "",
}

export function adyenConfigReducer(adyenConfig: AdyenConfigData, action: RootAction): AdyenConfigData {
    switch (action.type) {
        case "adyenConfig/setAdyenConfig":
            return action.payload

        default:
            return adyenConfig;
    }
}

interface SetAdyenConfigAction extends PayloadAction<"adyenConfig/setAdyenConfig", AdyenConfigData> {}


export type AdyenConfigAction = SetAdyenConfigAction