import {Notification} from "../reducers/types";
import {ErrorResponse} from "../types/errorResponse";

export function createError(errorResponse: ErrorResponse, isRedirect:boolean = false): Notification {
    return {
        errorResponse: errorResponse,
        notificationType: "error",
        isRedirect: isRedirect
    }
}

export function createWarn(errorCode: string, isRedirect:boolean = false): Notification {
    return {
        errorResponse: createResponseData(errorCode),
        notificationType: "warn",
        isRedirect: isRedirect
    }
}

export function createDefaultResponseData(): ErrorResponse {
    return {
        errorCode: "checkout.error.default",
        invalidFields: []
    }
}

export function createResponseData(errorCode: string): ErrorResponse {
    return {
        errorCode: errorCode,
        invalidFields: []
    }
}
