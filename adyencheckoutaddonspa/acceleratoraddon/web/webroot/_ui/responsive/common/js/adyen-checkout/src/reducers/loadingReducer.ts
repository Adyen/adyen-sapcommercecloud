import {RootAction} from "./rootReducer";
import {LoadingState} from "../types/loadingState";
import {Action} from "redux";

export const loadingStateInitialState: LoadingState = {
    loadingCartData: false
}

export function loadingReducer(loadingState: LoadingState, action: RootAction): LoadingState {
    switch (action.type) {
        case "loading/cartData/start":
            return {
                ...loadingState,
                loadingCartData: true
            }

        case "loading/cartData/end":
            return {
                ...loadingState,
                loadingCartData: false
            }


        default:
            return loadingState;
    }
}

interface SetCartDataLoading extends Action<"loading/cartData/start"> {
}

interface SetCartDataFinishedLoading extends Action<"loading/cartData/end"> {
}


export type LoadingAction = SetCartDataLoading | SetCartDataFinishedLoading;