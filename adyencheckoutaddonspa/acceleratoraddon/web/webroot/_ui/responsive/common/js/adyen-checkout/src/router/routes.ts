import {urlContextPath} from "../util/baseUrlUtil";

export const routes = {
    shippingAddress: urlContextPath + "/checkout/multi/adyen/shipping-address",
    shippingMethod:  urlContextPath + "/checkout/multi/adyen/shipping-method",
    paymentMethod:  urlContextPath + "/checkout/multi/adyen/payment-method",
    thankYouPage:  urlContextPath + "/checkout/multi/adyen/order-confirmation",
    shippingAddressRedirect: urlContextPath + "/checkout/multi/adyen/shipping-address/missingDataRedirect",
    shippingMethodRedirect: urlContextPath + "checkout/multi/adyen/shipping-method/missingDataRedirect"
}