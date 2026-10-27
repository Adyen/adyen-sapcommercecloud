import axios, {AxiosInstance} from "axios";
import {ErrorHandler} from "../errorhandler/ErrorHandler";


export const adyenAxios: AxiosInstance = axios.create({
    maxRedirects: 0,
})
adyenAxios.interceptors.response.use(
    response => {
        const responseURL = response.request?.responseURL;
        const requestURL = response.config.url;
        if (responseURL && requestURL) {
            const responseAddress = new URL(responseURL, window.location.href);
            const requestAddress = new URL(requestURL, window.location.href);
            if (responseAddress.origin !== requestAddress.origin || responseAddress.pathname !== requestAddress.pathname) {
                window.location.replace(responseURL);
            }
        }
        return response
    },
    error => {
        ErrorHandler.handleError(error)
        return Promise.reject(error);
    }
);
