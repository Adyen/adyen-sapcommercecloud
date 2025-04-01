import {Reducer} from "@reduxjs/toolkit";
import {addressInitialState, ShippingAddressAction, shippingAddressReducer} from "./shippingAddressReducer";
import {Action} from "redux";
import {
    AddressConfigAction,
    addressConfigInitialState,
    AddressConfigModel,
    addressConfigReducer
} from "./addressConfigReducer";
import {AddressModel, Notification, ShippingMethodState} from "./types";
import {AddressBookAction, addressBookInitialState, addressBookReducer} from "./addressBookReducer";
import {shippingMethodInitialState, shippingMethodReducer, ShippingModeAction} from "./shippingMethodReducer";
import {CartData} from "../types/cartData";
import {CartDataAction, cartDataInitialState, cartDataReducer} from "./cartDataReducer";
import {BillingAddressAction, billingAddressReducer} from "./billingAddressReducer";
import {AdyenConfigAction, adyenConfigInitialState, adyenConfigReducer} from "./adyenConfigReducer";
import {AdyenConfigData} from "../types/adyenConfigData";
import {LoadingAction, loadingReducer, loadingStateInitialState} from "./loadingReducer";
import {LoadingState} from "../types/loadingState";
import {NotificationAction, notificationInitialState, notificationReducer} from "./notificationReducer";

export const initialState: AppState = {
    shippingAddress: addressInitialState,
    addressConfig: addressConfigInitialState,
    addressBook: addressBookInitialState,
    shippingMethod: shippingMethodInitialState,
    cartData: cartDataInitialState,
    billingAddress: addressInitialState,
    adyenConfig: adyenConfigInitialState,
    loadingState: loadingStateInitialState,
    notifications: notificationInitialState
}


export const rootReducer: Reducer<AppState, RootAction, AppState> = function (appState: AppState, action: RootAction): AppState {
    return {
        shippingAddress: shippingAddressReducer(appState.shippingAddress, action),
        addressConfig: addressConfigReducer(appState.addressConfig, action),
        addressBook: addressBookReducer(appState.addressBook, action),
        shippingMethod: shippingMethodReducer(appState.shippingMethod, action),
        cartData: cartDataReducer(appState.cartData, action),
        billingAddress: billingAddressReducer(appState.billingAddress, action),
        adyenConfig: adyenConfigReducer(appState.adyenConfig, action),
        loadingState: loadingReducer(appState.loadingState, action),
        notifications: notificationReducer(appState.notifications, action)
    }
}

export interface AppState {
    shippingAddress: AddressModel
    addressConfig: AddressConfigModel
    addressBook: AddressModel[]
    shippingMethod: ShippingMethodState,
    cartData: CartData,
    billingAddress: AddressModel,
    adyenConfig: AdyenConfigData,
    loadingState: LoadingState,
    notifications: Notification[],
}

export type RootAction =
    ShippingAddressAction
    | AddressConfigAction
    | AddressBookAction
    | ShippingModeAction
    | CartDataAction
    | BillingAddressAction
    | AdyenConfigAction
    | LoadingAction
    | NotificationAction

export interface PayloadAction<T extends string, PT = string> extends Action<T> {
    payload: PT
}