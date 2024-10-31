import {AxiosError, AxiosResponse} from "axios";
import {CSRFToken, urlContextPath} from "../util/baseUrlUtil";
import {AddressData, PlaceOrderRequest} from "../types/paymentForm";
import {AddressModel} from "../reducers/types";
import {PaymentAction} from "@adyen/adyen-web";
import {ErrorResponse} from "../types/errorResponse";
import {adyenAxios} from "../axios/AdyenAxios";

export interface PlaceOrderResponse {
    success: boolean,
    executeAction?: boolean,
    paymentsAction?: PaymentAction,
    error?: string,
    errorFieldCodes?: string[]
    orderNumber?: string
}

export class PaymentService {
    static async placeOrder(paymentForm: PlaceOrderRequest) {
        return adyenAxios.post<PlaceOrderResponse>(urlContextPath + '/api/checkout/place-order', paymentForm, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then((response: AxiosResponse<any>): PlaceOrderResponse => {
                let placeOrderData = response.data;

                return {
                    success: true,
                    executeAction: placeOrderData.executeAction,
                    paymentsAction: placeOrderData.paymentsAction,
                    orderNumber: placeOrderData.orderNumber
                }
            })
            .catch((errorResponse: AxiosError<ErrorResponse>): PlaceOrderResponse | void => {
                console.error('Error on place order')
                if (errorResponse.response.status === 400) {
                    return {
                        success: false,
                        error: errorResponse.response.data.errorCode,
                        errorFieldCodes: errorResponse.response.data.invalidFields
                    }
                }
            })
    }

    static async sendAdditionalDetails(details: any) {
        return adyenAxios.post<PlaceOrderResponse>(urlContextPath + '/api/checkout/additional-details', details, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then((response: AxiosResponse<any>): PlaceOrderResponse => {
                let placeOrderData = response.data;

                return {
                    success: true,
                    executeAction: placeOrderData.executeAction,
                    paymentsAction: placeOrderData.paymentsAction,
                    orderNumber: placeOrderData.orderNumber
                }
            })
            .catch((errorResponse: AxiosError<ErrorResponse>): PlaceOrderResponse | void => {
                console.error('Error on place order')
                if (errorResponse.response.status === 400) {
                    return {
                        success: false,
                        error: errorResponse.response.data.errorCode,
                        errorFieldCodes: errorResponse.response.data.invalidFields
                    }
                }
            })
    }

    static async sendPaymentCancel() {
        return adyenAxios.post<void>(urlContextPath + '/api/checkout/payment-canceled', {}, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
    }

    static convertBillingAddress(address: AddressModel, saveInAddressBook: boolean): AddressData {
        return {
            addressId: address.id,
            countryIso: address.countryCode,
            regionIso: address.regionCode,
            firstName: address.firstName,
            lastName: address.lastName,
            line1: address.line1,
            line2: address.line2,
            phoneNumber: address.phoneNumber,
            postcode: address.postalCode,
            titleCode: address.titleCode,
            townCity: address.city,
            saveInAddressBook: saveInAddressBook,
        }
    }

    static preparePlaceOrderRequest(data: any, useDifferentBillingAddress: boolean, saveInAddressBook: boolean, billingAddress?: AddressModel): PlaceOrderRequest {
        return {
            paymentRequest: data,
            useAdyenDeliveryAddress: !useDifferentBillingAddress,
            billingAddress: useDifferentBillingAddress ? this.convertBillingAddress(billingAddress, saveInAddressBook) : null,

        }
    }
}