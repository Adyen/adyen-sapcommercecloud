import {PayloadAction, RootAction} from "./rootReducer";
import {AddressModel} from "./types";


export function billingAddressReducer(addressState: AddressModel, action: RootAction): AddressModel {
    switch (action.type) {
        case "billingAddress/setFirstName":
            return {
                ...addressState,
                firstName: action.payload
            }

        case "billingAddress/setLastName":
            return {
                ...addressState,
                lastName: action.payload
            }

        case "billingAddress/setCountryCode":
            return {
                ...addressState,
                countryCode: action.payload,
                regionCode: null
            }

        case "billingAddress/setRegionCode":
            return {
                ...addressState,
                regionCode: action.payload
            }

        case "billingAddress/setTitleCode":
            return {
                ...addressState,
                titleCode: action.payload
            }

        case "billingAddress/setLine1":
            return {
                ...addressState,
                line1: action.payload
            }

        case "billingAddress/setLine2":
            return {
                ...addressState,
                line2: action.payload
            }

        case "billingAddress/setCity":
            return {
                ...addressState,
                city: action.payload
            }

        case "billingAddress/setPostCode":
            return {
                ...addressState,
                postalCode: action.payload
            }

        case "billingAddress/setPhoneNumber":
            return {
                ...addressState,
                phoneNumber: action.payload
            }

        case "billingAddress/setAddress":
            return action.payload

        default:
            return addressState;
    }
}


interface SetBAFirstNameAction extends PayloadAction<"billingAddress/setFirstName"> {
}

interface SetBALastNameAction extends PayloadAction<"billingAddress/setLastName"> {
}

interface SetBACountryCodeAction extends PayloadAction<"billingAddress/setCountryCode"> {
}

interface SetBARegionCodeAction extends PayloadAction<"billingAddress/setRegionCode"> {
}

interface SetBATitleCodeAction extends PayloadAction<"billingAddress/setTitleCode"> {
}

interface SetBALine1Action extends PayloadAction<"billingAddress/setLine1"> {
}

interface SetBALine2Action extends PayloadAction<"billingAddress/setLine2"> {
}

interface SetBACityAction extends PayloadAction<"billingAddress/setCity"> {
}

interface SetBAPostCodeAction extends PayloadAction<"billingAddress/setPostCode"> {
}

interface SetBAPhoneNumberAction extends PayloadAction<"billingAddress/setPhoneNumber"> {
}

interface SetBAAddress extends PayloadAction<"billingAddress/setAddress", AddressModel> {
}


export type BillingAddressAction =
    SetBAFirstNameAction
    | SetBALastNameAction
    | SetBACountryCodeAction
    | SetBARegionCodeAction
    | SetBATitleCodeAction
    | SetBALine1Action
    | SetBALine2Action
    | SetBACityAction
    | SetBAPostCodeAction
    | SetBAPhoneNumberAction
    | SetBAAddress