import {AxiosError} from "axios";
import {ErrorResponse} from "../types/errorResponse";
import {isEmpty} from "../util/stringUtil";
import {store} from "../store/store";
import {createDefaultResponseData, createError} from "../util/notificationUtil";

export class ErrorHandler {

    public static handleError(error?: AxiosError<ErrorResponse>) {
        let errorResponseData
        if (!error || !error.response || !error.response.data || isEmpty(error.response.data.errorCode)) {
            errorResponseData = createDefaultResponseData()
        } else {
            errorResponseData = error.response.data;
        }
        store.dispatch({type: "notifications/addNotification", payload: createError(errorResponseData)})
    }
}