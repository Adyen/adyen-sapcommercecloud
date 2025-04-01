import {CartData} from "../types/cartData";
import {PayloadAction, RootAction} from "./rootReducer";

export const cartDataInitialState: CartData = {
    adyenAmazonPayConfiguration: undefined,
    adyenApplePayMerchantIdentifier: "",
    adyenApplePayMerchantName: "",
    adyenBillingAddress: undefined,
    adyenBoletoBarCodeReference: "",
    adyenBoletoData: "",
    adyenBoletoDueDate: undefined,
    adyenBoletoExpirationDate: undefined,
    adyenBoletoUrl: "",
    adyenBrowserInfo: "",
    adyenCardBrand: "",
    adyenCardHolder: "",
    adyenCardType: "",
    adyenCseToken: "",
    adyenDfValue: "",
    adyenDob: undefined,
    adyenEncryptedCardNumber: "",
    adyenEncryptedExpiryMonth: "",
    adyenEncryptedExpiryYear: "",
    adyenEncryptedSecurityCode: "",
    adyenFirstName: "",
    adyenGiftCardBrand: "",
    adyenInstallments: 0,
    adyenIssuerId: "",
    adyenLastName: "",
    adyenMultibancoAmount: 0,
    adyenMultibancoDeadline: "",
    adyenMultibancoEntity: "",
    adyenMultibancoReference: "",
    adyenPaymentMethod: "",
    adyenPosReceipt: "",
    adyenRememberTheseDetails: false,
    adyenReturnUrl: "",
    adyenSelectedReference: "",
    adyenSepaIbanNumber: "",
    adyenSepaOwnerName: "",
    adyenShopperEmail: "",
    adyenShopperGender: "",
    adyenShopperTelephone: "",
    adyenSocialSecurityNumber: "",
    adyenTerminalId: "",
    adyenUPIVirtualAddress: "",
    calculated: false,
    code: "",
    deliveryAddress: undefined,
    deliveryCost: undefined,
    deliveryMode: undefined,
    description: "",
    entries: [],
    expirationTime: undefined,
    name: "",
    net: false,
    subTotal: undefined,
    subTotalWithoutQuoteDiscounts: undefined,
    subscriptionOrder: false,
    totalItems: 0,
    totalPrice: undefined,
    totalPriceWithTax: undefined,
    totalTax: undefined,
    pickupOrderGroups: undefined,
    totalDiscounts: undefined,
    appliedProductPromotions: undefined
}

export function cartDataReducer(cartData: CartData, action: RootAction): CartData {
    switch (action.type) {

        case "cartData/setCartData":
            return action.payload

        default:
            return cartData;
    }
}

export interface SetCartDataAction extends PayloadAction<"cartData/setCartData", CartData> {
}

export type CartDataAction = SetCartDataAction