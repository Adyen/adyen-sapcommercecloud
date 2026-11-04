import {AxiosError, AxiosResponse} from "axios";
import {urlContextPath} from "../util/baseUrlUtil";
import {store} from "../store/store";
import {AdyenConfigData, DisclaimerConfigurationData} from "../types/adyenConfigData";
import {ErrorResponse} from "../types/errorResponse";
import {adyenAxios} from "../axios/AdyenAxios";

export class AdyenConfigService {
    private static configRequestId = 0;

    public static fetchPaymentMethodsConfig(shopperCountryCode: string) {
        const requestId = ++AdyenConfigService.configRequestId;
        adyenAxios.get(urlContextPath + '/api/checkout/payment-methods-configuration', {
            params: { shopperCountryCode },
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        })
            .then((response: AxiosResponse<AdyenConfigData>) => {
                if (requestId === AdyenConfigService.configRequestId) {
                    store.dispatch({type: "adyenConfig/setAdyenConfig", payload: response.data})
                }
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Payment method config fetch error.")
                return false
            })
    }

    public static fetchDisclaimerConfiguration(shopperCountryCode: string) {
        const requestId = ++AdyenConfigService.configRequestId;
        adyenAxios.get(urlContextPath + '/api/checkout/disclaimer-configuration', {
            params: {shopperCountryCode},
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        })
            .then((response: AxiosResponse<DisclaimerConfigurationData>) => {
                if (requestId === AdyenConfigService.configRequestId) {
                    store.dispatch({type: "adyenConfig/updateDisclaimer", payload: response.data});
                }
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Disclaimer configuration fetch error.");
                return false;
            });
    }
}
