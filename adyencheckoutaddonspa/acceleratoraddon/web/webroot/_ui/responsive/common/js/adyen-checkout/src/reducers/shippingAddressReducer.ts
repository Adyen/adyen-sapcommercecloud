import {PayloadAction, RootAction} from "./rootReducer";
import {AddressModel} from "./types";


export const addressInitialState: AddressModel = {
    id: "",
    firstName: "",
    line1: "",
    line2: "",
    city: "",
    country: "",
    countryCode: "",
    regionCode: null,
    region: "",
    lastName: "",
    phoneNumber: "",
    postalCode: "",
    title: "",
    titleCode: ""
}

export function shippingAddressReducer(addressState: AddressModel, action: RootAction): AddressModel {
    switch (action.type) {
        case "shippingAddress/setFirstName":
            return {
                ...addressState,
                firstName: action.payload
            }

        case "shippingAddress/setLastName":
            return {
                ...addressState,
                lastName: action.payload
            }

        case "shippingAddress/setCountryCode":
            return {
                ...addressState,
                countryCode: action.payload,
                regionCode: null
            }

        case "shippingAddress/setRegionCode":
            return {
                ...addressState,
                regionCode: action.payload
            }

        case "shippingAddress/setTitleCode":
            return {
                ...addressState,
                titleCode: action.payload
            }

        case "shippingAddress/setLine1":
            return {
                ...addressState,
                line1: action.payload
            }

        case "shippingAddress/setLine2":
            return {
                ...addressState,
                line2: action.payload
            }

        case "shippingAddress/setCity":
            return {
                ...addressState,
                city: action.payload
            }

        case "shippingAddress/setPostCode":
            return {
                ...addressState,
                postalCode: action.payload
            }

        case "shippingAddress/setPhoneNumber":
            return {
                ...addressState,
                phoneNumber: action.payload
            }

        case "shippingAddress/setAddress":
            return action.payload

        default:
            return addressState;
    }
}


interface SetSAFirstNameAction extends PayloadAction<"shippingAddress/setFirstName"> {
}

interface SetSALastNameAction extends PayloadAction<"shippingAddress/setLastName"> {
}

interface SetSACountryCodeAction extends PayloadAction<"shippingAddress/setCountryCode"> {
}

interface SetSARegionCodeAction extends PayloadAction<"shippingAddress/setRegionCode"> {
}

interface SetSATitleCodeAction extends PayloadAction<"shippingAddress/setTitleCode"> {
}

interface SetSALine1Action extends PayloadAction<"shippingAddress/setLine1"> {
}

interface SetSALine2Action extends PayloadAction<"shippingAddress/setLine2"> {
}

interface SetSACityAction extends PayloadAction<"shippingAddress/setCity"> {
}

interface SetSAPostCodeAction extends PayloadAction<"shippingAddress/setPostCode"> {
}

interface SetSAPhoneNumberAction extends PayloadAction<"shippingAddress/setPhoneNumber"> {
}

interface SetSAAddress extends PayloadAction<"shippingAddress/setAddress", AddressModel> {
}


export type ShippingAddressAction =
    SetSAFirstNameAction
    | SetSALastNameAction
    | SetSACountryCodeAction
    | SetSARegionCodeAction
    | SetSATitleCodeAction
    | SetSALine1Action
    | SetSALine2Action
    | SetSACityAction
    | SetSAPostCodeAction
    | SetSAPhoneNumberAction
    | SetSAAddress