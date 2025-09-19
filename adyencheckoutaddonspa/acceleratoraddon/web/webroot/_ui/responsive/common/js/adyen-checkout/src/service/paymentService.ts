import {AxiosError, AxiosResponse} from "axios";
import {CSRFToken, urlContextPath} from "../util/baseUrlUtil";
import {AddressData, PlaceOrderRequest} from "../types/paymentForm";
import {AddressModel} from "../reducers/types";
import {PaymentAction,PaymentResponseData} from "@adyen/adyen-web";
import {ErrorResponse} from "../types/errorResponse";
import {adyenAxios} from "../axios/AdyenAxios";
import { storefrontVersion } from '../version';

export interface PlaceOrderResponse {
    success: boolean,
    executeAction?: boolean,
    paymentsAction?: PaymentAction,
    paymentsResponse?: PaymentResponseData,
    paymentDetailsResponse?: PaymentResponseData,
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
                    orderNumber: placeOrderData.orderNumber,
                    paymentsResponse: placeOrderData.paymentsResponse
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
                    orderNumber: placeOrderData.orderNumber,
                    paymentsResponse: placeOrderData.paymentDetailsResponse
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
            companyName: address.companyName,
            taxNumber: address.taxNumber,
            registrationNumber: address.registrationNumber,
            saveInAddressBook: saveInAddressBook,
        }
    }

    static preparePlaceOrderRequest(data: any, useDifferentBillingAddress: boolean, saveInAddressBook: boolean, billingAddress?: AddressModel, partialPaymentId?: string): PlaceOrderRequest {
        return {
            paymentRequest: data,
            useAdyenDeliveryAddress: !useDifferentBillingAddress,
            billingAddress: useDifferentBillingAddress ? this.convertBillingAddress(billingAddress, saveInAddressBook) : null,
            storefrontType: "SPA",
            storefrontVersion: storefrontVersion,
            partialPaymentId: partialPaymentId,
        }
    }

    /**
     * Check gift card balance for partial payments
     * @param request Gift card balance request
     * @returns Promise with balance response
     */
    static async checkGiftCardBalance(request: {
        cardNumber: string;
        pin?: string;
        amount: { value: number; currency: string };
        brand: string;
        type: string;
    }) {
        return adyenAxios.post(urlContextPath + '/api/giftcard/balance', request, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then((response: AxiosResponse<any>) => {
                return response.data;
            })
            .catch((error: AxiosError) => {
                console.error('Error checking gift card balance:', error);
                throw error;
            });
    }

    /**
     * Create partial payment order
     * @param request Partial payment order request
     * @returns Promise with order response
     */
    static async createPartialPaymentOrder(request: {
        amount: { value: number; currency: string };
        paymentMethod: any;
        shopperReference?: string;
        partialPaymentId?: string;
    }) {
        return adyenAxios.post(urlContextPath + '/api/orders/partial-payment', request, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then((response: AxiosResponse<any>) => {
                return response.data;
            })
            .catch((error: AxiosError) => {
                console.error('Error creating partial payment order:', error);
                throw error;
            });
    }
}