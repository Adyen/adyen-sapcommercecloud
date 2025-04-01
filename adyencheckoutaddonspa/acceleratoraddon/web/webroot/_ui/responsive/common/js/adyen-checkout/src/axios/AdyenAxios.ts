import axios, {AxiosInstance} from "axios";
import {ErrorHandler} from "../errorhandler/ErrorHandler";


export const adyenAxios: AxiosInstance = axios.create({
    maxRedirects: 0,
})
adyenAxios.interceptors.response.use(
    response => {
        if (!response.request.responseURL.endsWith(response.config.url)) {
            window.location.replace(response.request.responseURL)
        }
        return response
    },
    error => {
        ErrorHandler.handleError(error)
        return Promise.reject(error);
    }
);