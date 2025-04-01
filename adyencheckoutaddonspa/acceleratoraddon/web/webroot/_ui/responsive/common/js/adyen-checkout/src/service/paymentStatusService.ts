import {AxiosError} from "axios";
import {CSRFToken, urlContextPath} from "../util/baseUrlUtil";
import {ErrorResponse} from "../types/errorResponse";
import {adyenAxios} from "../axios/AdyenAxios";

export class PaymentStatusService {
    static fetchPaymentStatus(orderCode: string) {
        return adyenAxios.post(urlContextPath + '/api/checkout/get-payment-status', orderCode, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then(response => {
                return response.data
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Payment status fetch error.")
                return false
            })
    }

    static fetchOrderCodeForGUID(orderGUID: string) {
        return adyenAxios.post(urlContextPath + '/api/checkout/get-order-code', orderGUID, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then(response => {
                return response.data
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Payment status fetch error.")
                return false
            })
    }
}