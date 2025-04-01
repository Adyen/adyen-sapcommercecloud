import {AxiosError} from "axios";
import {CSRFToken, urlContextPath} from "../util/baseUrlUtil";
import {AddressModel, RegionModel} from "../reducers/types";
import {store} from "../store/store";
import {isNotEmpty} from "../util/stringUtil";
import {AddressConfigModel} from "../reducers/addressConfigReducer";
import {AddressData, RegionData} from "../types/addressData";
import {ErrorResponse} from "../types/errorResponse";
import {adyenAxios} from "../axios/AdyenAxios";

interface AddDeliveryAddressResponse {
    success: boolean,
    errorFieldCodes: string[]
}

export class AddressService {

    static fetchAddressBook() {
        adyenAxios.get(urlContextPath + '/api/account/delivery-address', {
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        })
            .then(response => {
                let addressModels = this.mapResponseDataToModel(response.data);
                store.dispatch({type: "addressBook/setAddressBook", payload: addressModels})
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Address book fetch error")
                return false
            })
    }

    static async selectDeliveryAddress(addressId: string) {
        return adyenAxios.post(urlContextPath + '/api/checkout/delivery-address', addressId, {
            headers: {
                'Content-Type': 'text/plain',
                'CSRFToken': CSRFToken
            }
        })
            .then(() => true)
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error('Error on address select')
                return false
            })
    }

    static async addDeliveryAddress(address: AddressModel, saveInAddressBook: boolean, isShippingAddress: boolean, isBillingAddress: boolean,
                                    editAddress: boolean): Promise<AddDeliveryAddressResponse> {
        const payload = this.mapAddressModelToAddressForm(address, saveInAddressBook, isShippingAddress, isBillingAddress, editAddress);
        return adyenAxios.post(urlContextPath + '/api/account/delivery-address', payload, {
            headers: {
                'Content-Type': 'application/json; charset=utf-8',
                'CSRFToken': CSRFToken
            }
        }).then((): AddDeliveryAddressResponse => {
            return {success: true, errorFieldCodes: []}
        })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error('Error on address select')
                return {
                    success: false,
                    errorFieldCodes: errorResponse.response.data.invalidFields
                }
            })
    }

    static fetchAddressConfig() {
        adyenAxios.get(urlContextPath + '/api/configuration/shipping-address', {
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        })
            .then(response => {
                let addressConfigModel = this.mapAddressConfigurationResponse(response.data);
                store.dispatch({type: "addressConfig/setAddressConfig", payload: addressConfigModel})
            })
            .catch((errorResponse: AxiosError<ErrorResponse>) => {
                console.error("Address config fetch error")
                return false
            })
    }

    private static mapResponseDataToModel(data: AddressData[]): AddressModel[] {
        return data.map(it => this.mapAddressBookDataToAddressModel(it));
    }

    private static mapAddressBookDataToAddressModel(responseData: AddressData): AddressModel {
        return {
            id: responseData.id,
            firstName: responseData.firstName,
            lastName: responseData.lastName,
            titleCode: responseData.titleCode,
            title: responseData.title,
            countryCode: responseData.country.isocode,
            country: responseData.country.name,
            regionCode: responseData.region ? responseData.region.isocode : null,
            region: responseData.region ? responseData.region.name : null,
            line1: responseData.line1,
            line2: responseData.line2,
            city: responseData.town,
            postalCode: responseData.postalCode,
            phoneNumber: responseData.phone
        }
    }

    private static mapAddressModelToAddressForm(addressModel: AddressModel, saveInAddressBook: boolean, isShippingAddress: boolean,
                                                isBillingAddress: boolean, editAddress: boolean): AddressForm {
        return {
            addressId: isNotEmpty(addressModel.id) ? addressModel.id : null,
            titleCode: addressModel.titleCode,
            firstName: addressModel.firstName,
            lastName: addressModel.lastName,
            line1: addressModel.line1,
            line2: addressModel.line2,
            townCity: addressModel.city,
            regionIso: addressModel.regionCode,
            postcode: addressModel.postalCode,
            countryIso: addressModel.countryCode,
            saveInAddressBook: saveInAddressBook,
            shippingAddress: isShippingAddress,
            billingAddress: isBillingAddress,
            editAddress: editAddress,
            phone: addressModel.phoneNumber,
            defaultAddress: false,
        }
    }

    private static mapAddressConfigurationResponse(response: AddressConfigResponse): AddressConfigModel {
        let titles = response.titles.map(title => {
            return {code: title.code, value: title.name}
        });

        let countries = response.countries.map(country => {
            return {code: country.isocode, value: country.name}
        });

        let regions: RegionModel[] = response.regions.map(region => {
            return {
                countryCode: region.countryCode,
                regions: region.regionData.map(r => {
                    return {code: r.isocode, value: r.name}
                })
            }
        })

        return {
            anonymousUser: response.anonymous,
            titles: titles,
            regions: regions,
            countries: countries,
        }
    }
}

interface AddressForm {
    addressId: string
    titleCode: string
    firstName: string
    lastName: string
    line1: string
    line2: string
    townCity: string
    regionIso: string
    postcode: string
    countryIso: string
    saveInAddressBook: boolean
    defaultAddress: boolean
    shippingAddress: boolean
    billingAddress: boolean
    editAddress: boolean
    phone: string
}

interface CountryData {
    isocode: string,
    name: string
}

interface TitleData {
    code: string,
    name: string
}

export interface Region {
    countryCode: string,
    regionData: RegionData[]
}

interface AddressConfigResponse {
    anonymous: boolean,
    countries: CountryData[],
    titles: TitleData[],
    regions: Region[]
}
