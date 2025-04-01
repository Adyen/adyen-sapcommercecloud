import {InputDropdown} from "../controls/InputDropdown";
import {InputText} from "../controls/InputText";
import React from "react";
import {AddressConfigModel} from "../../reducers/addressConfigReducer";
import {AddressModel, CodeValueItem} from "../../reducers/types";
import {translationsStore} from "../../store/translationsStore";

interface Props {
    addressConfig: AddressConfigModel,
    address: AddressModel,
    errorFieldCodes: string[]
    errorFieldCodePrefix: string
    onCountryCodeChange: (countryCode: string) => void,
    onRegionCodeChange: (regionCode: string) => void,
    onTitleCodeChange: (titleCode: string) => void,
    onFirstNameChange: (firstName: string) => void,
    onLastNameChange: (lastName: string) => void,
    onLine1Change: (line1: string) => void,
    onLine2Change: (line2: string) => void,
    onCityChange: (city: string) => void,
    onPostCodeChange: (postCode: string) => void,
    onPhoneNumberChange: (phoneNumber: string) => void
}

export class AddressForm extends React.Component<Props, any> {

    private renderRegions() {
        let regions = this.getRegionsByCountryCode(this.props.address.countryCode)

        if (regions.length > 0) {
            return <InputDropdown testId={"address.region"}
                                  values={regions}
                                  fieldName={translationsStore.get("address.state")}
                                  onChange={(regionCode) => this.props.onRegionCodeChange(regionCode)}
                                  selectedValue={this.props.address.regionCode}
                                  placeholderText={translationsStore.get("address.selectState")}
                                  placeholderDisabled={true}
                                  fieldErrorId={this.props.errorFieldCodePrefix + "regionIso"}
                                  fieldErrorTextCode="address.regionIso.invalid"
                                  fieldErrors={this.props.errorFieldCodes}/>
        }

        return <></>
    }

    private getRegionsByCountryCode(countryCode: string): CodeValueItem[] {
        let region = this.props.addressConfig.regions.find((region) => region.countryCode === countryCode);
        if (region) {
            return region.regions
        }
        return []
    }

    render() {
        return <>
            <InputDropdown testId={"address.country"}
                           values={this.props.addressConfig.countries}
                           fieldName={translationsStore.get("address.country")}
                           onChange={(countryCode) => this.props.onCountryCodeChange(countryCode)}
                           selectedValue={this.props.address.countryCode}
                           placeholderText={translationsStore.get("address.selectCountry")} placeholderDisabled={true}
                           fieldErrorId={this.props.errorFieldCodePrefix + "countryIso"}
                           fieldErrorTextCode="address.country.invalid"
                           fieldErrors={this.props.errorFieldCodes}/>
            <InputDropdown testId={"address.title"}
                           values={this.props.addressConfig.titles} fieldName={translationsStore.get("address.title")}
                           onChange={(titleCode) => this.props.onTitleCodeChange(titleCode)}
                           selectedValue={this.props.address.titleCode}
                           placeholderText={translationsStore.get("address.title.none")}
                           fieldErrorId={this.props.errorFieldCodePrefix + "titleCode"}
                           fieldErrorTextCode="address.title.invalid"
                           fieldErrors={this.props.errorFieldCodes}/>
            <InputText testId={"address.firstName"}
                       fieldName={translationsStore.get("address.firstName")}
                       onChange={(firstName) => this.props.onFirstNameChange(firstName)}
                       value={this.props.address.firstName}
                       fieldErrorId={this.props.errorFieldCodePrefix + "firstName"}
                       fieldErrorTextCode="address.firstName.invalid"
                       fieldErrors={this.props.errorFieldCodes}/>
            <InputText testId={"address.surname"}
                       fieldName={translationsStore.get("address.surname")}
                       onChange={(lastName) => this.props.onLastNameChange(lastName)}
                       value={this.props.address.lastName}
                       fieldErrorId={this.props.errorFieldCodePrefix + "lastName"}
                       fieldErrorTextCode="address.lastName.invalid"
                       fieldErrors={this.props.errorFieldCodes}/>
            <InputText testId={"address.line1"}
                       fieldName={translationsStore.get("address.line1")}
                       onChange={(line1) => this.props.onLine1Change(line1)}
                       value={this.props.address.line1}
                       fieldErrorId={this.props.errorFieldCodePrefix + "line1"}
                       fieldErrorTextCode="address.line1.invalid"
                       fieldErrors={this.props.errorFieldCodes}/>
            <InputText testId={"address.line2"}
                       fieldName={translationsStore.get("address.line2")}
                       onChange={(line2) => this.props.onLine2Change(line2)}
                       value={this.props.address.line2}
                       fieldErrorId={this.props.errorFieldCodePrefix + "line2"}
                       fieldErrorTextCode="address.line2.invalid"
                       fieldErrors={this.props.errorFieldCodes}/>
            <InputText testId={"address.townCity"}
                       fieldName={translationsStore.get("address.townCity")}
                       onChange={(city) => this.props.onCityChange(city)}
                       value={this.props.address.city}
                       fieldErrorId={this.props.errorFieldCodePrefix + "townCity"}
                       fieldErrorTextCode="address.townCity.invalid"
                       fieldErrors={this.props.errorFieldCodes}/>
            {this.renderRegions()}
            <InputText testId={"address.postcode"}
                       fieldName={translationsStore.get("address.postcode")}
                       onChange={(postCode) => this.props.onPostCodeChange(postCode)}
                       value={this.props.address.postalCode}
                       fieldErrorId={this.props.errorFieldCodePrefix + "postcode"}
                       fieldErrorTextCode="address.postcode.invalid"
                       fieldErrors={this.props.errorFieldCodes}/>
            <InputText testId={"address.phone"}
                       fieldName={translationsStore.get("address.phone")}
                       onChange={(phoneNumber) => this.props.onPhoneNumberChange(phoneNumber)}
                       value={this.props.address.phoneNumber}/>
        </>;
    }
}