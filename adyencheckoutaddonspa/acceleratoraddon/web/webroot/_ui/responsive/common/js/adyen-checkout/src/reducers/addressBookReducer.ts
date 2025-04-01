import {AddressModel} from "./types";
import {PayloadAction, RootAction} from "./rootReducer";

export const addressBookInitialState: AddressModel[] = []

export function addressBookReducer(addressBookState: AddressModel[], action: RootAction): AddressModel[] {
    switch (action.type) {
        case "addressBook/setAddressBook": {
            return action.payload
        }

        default:
            return addressBookState
    }
}

export interface SetAddressBookAction extends PayloadAction<"addressBook/setAddressBook", AddressModel[]> {}

export type AddressBookAction = SetAddressBookAction