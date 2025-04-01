import {ShippingMethodModel, ShippingMethodState} from "./types";
import {PayloadAction, RootAction} from "./rootReducer";

export const shippingMethodInitialState: ShippingMethodState = {
    selectedShippingMethodCode: "",
    shippingMethods: []
};

export function shippingMethodReducer(shippingMethodState: ShippingMethodState, action: RootAction): ShippingMethodState {
    switch (action.type) {
        case "shippingMethod/setAvailableShippingMethods":
            return {
                ...shippingMethodState,
                shippingMethods: action.payload
            }

        case "shippingMethod/setShippingMethod":
            return {
                ...shippingMethodState,
                selectedShippingMethodCode: action.payload
            }

        default:
            return shippingMethodState
    }

}

export interface SetAvailableShippingMethodsAction extends PayloadAction<"shippingMethod/setAvailableShippingMethods", ShippingMethodModel[]> {
}

export interface SetShippingMethodAction extends PayloadAction<"shippingMethod/setShippingMethod"> {
}

export type ShippingModeAction = SetAvailableShippingMethodsAction | SetShippingMethodAction

