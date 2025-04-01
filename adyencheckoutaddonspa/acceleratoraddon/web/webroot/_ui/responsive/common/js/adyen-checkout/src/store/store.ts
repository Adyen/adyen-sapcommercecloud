import {configureStore} from "@reduxjs/toolkit";
import {initialState, rootReducer} from "../reducers/rootReducer";


export const store = configureStore({reducer: rootReducer, preloadedState: initialState})

export type StoreDispatch = typeof store.dispatch


