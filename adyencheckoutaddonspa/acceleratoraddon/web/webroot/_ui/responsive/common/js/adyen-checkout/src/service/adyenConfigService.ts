import {AxiosError, AxiosResponse} from "axios";
import {urlContextPath} from "../util/baseUrlUtil";
import {store} from "../store/store";
import {AdyenConfigData} from "../types/adyenConfigData";
import {ErrorResponse} from "../types/errorResponse";
import {adyenAxios} from "../axios/AdyenAxios";

export class AdyenConfigService {

    public static fetchPaymentMethodsConfig() {
        adyenAxios.get(urlContextPath + '/api/checkout/payment-methods-configuration', {
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        })
            .then((response: AxiosResponse<AdyenConfigData>) => {
                store.dispatch({type: "adyenConfig/setAdyenConfig", payload: response.data})
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Payment method config fetch error.")
                return false
            })
    }
}