import React from "react";
import {AddressForm} from "./AddressForm";
import {InputCheckbox} from "../controls/InputCheckbox";
import {translationsStore} from "../../store/translationsStore";
import AddressBookSelector from "../shipping-address/AddressBookSelector";
import {AddressService} from "../../service/addressService";
import {AddressModel} from "../../reducers/types";
import {AddressConfigModel} from "../../reducers/addressConfigReducer";
import {connect} from "react-redux";
import {AppState} from "../../reducers/rootReducer";

interface StoreProps {
    addressConfig: AddressConfigModel
    addressBook: AddressModel[]
}

interface ComponentProps {
    address: AddressModel
    addressConfig: AddressConfigModel
    addressBook: AddressModel[]
    saveInAddressBook: boolean
    errorFieldCodes: string[]
    errorFieldCodePrefix: string

    onSelectAddress: (address: AddressModel) => void

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
    onChangeSaveInAddressBook: (value: boolean) => void
}

type Props = ComponentProps & StoreProps

interface State {
    addressBookModalOpen: boolean
}

class AddressSection extends React.Component<Props, State> {

    constructor(props: any) {
        super(props);
        this.state = {
            addressBookModalOpen: false
        }
    }

    componentDidMount() {
        AddressService.fetchAddressBook()
        AddressService.fetchAddressConfig()
    }

    private openAddressBookModal() {
        this.setState({addressBookModalOpen: true})
    }

    private closeAddressBookModal() {
        this.setState({addressBookModalOpen: false})
    }

    private renderSaveAddressCheckbox(): React.JSX.Element {
        if (!this.props.addressConfig.anonymousUser) {
            return <InputCheckbox
                fieldName={translationsStore.get("checkout.summary.deliveryAddress.saveAddressInMyAddressBook")}
                onChange={(value) => this.props.onChangeSaveInAddressBook(value)}
                checked={this.props.saveInAddressBook}/>
        }
        return <></>
    }

    private renderAddressBookButton(): React.JSX.Element {
        if (this.props.addressBook.length > 0) {
            return <button className={"btn btn-default btn-block"}
                           onClick={() => this.openAddressBookModal()}>{translationsStore.get("checkout.checkout.multi.deliveryAddress.viewAddressBook")}</button>
        }
        return <></>
    }

    private renderAddressBookModal(): React.JSX.Element {
        if (this.state.addressBookModalOpen) {
            return <AddressBookSelector closeModal={() => this.closeAddressBookModal()}
                                        onSelectAddress={this.props.onSelectAddress}/>
        } else {
            return <></>
        }
    }

    render() {
        return (
            <>
                {this.renderAddressBookModal()}
                {this.renderAddressBookButton()}
                <br/>
                <AddressForm addressConfig={this.props.addressConfig}
                             onCountryCodeChange={(countryCode) => this.props.onCountryCodeChange(countryCode)}
                             onRegionCodeChange={(regionCode) => this.props.onRegionCodeChange(regionCode)}
                             address={this.props.address}
                             errorFieldCodes={this.props.errorFieldCodes}
                             errorFieldCodePrefix={this.props.errorFieldCodePrefix}
                             onTitleCodeChange={(titleCode) => this.props.onTitleCodeChange(titleCode)}
                             onFirstNameChange={(firstName) => this.props.onFirstNameChange(firstName)}
                             onLastNameChange={(lastName) => this.props.onLastNameChange(lastName)}
                             onLine1Change={(line1) => this.props.onLine1Change(line1)}
                             onLine2Change={(line2) => this.props.onLine2Change(line2)}
                             onCityChange={(city) => this.props.onCityChange(city)}
                             onPostCodeChange={(postCode) => this.props.onPostCodeChange(postCode)}
                             onPhoneNumberChange={(phoneNumber) => this.props.onPhoneNumberChange(phoneNumber)}/>
                {this.renderSaveAddressCheckbox()}
            </>
        )
    }
}

const mapStateToProps = (state: AppState): StoreProps => ({
    addressConfig: state.addressConfig,
    addressBook: state.addressBook
})
export default connect(mapStateToProps)(AddressSection)