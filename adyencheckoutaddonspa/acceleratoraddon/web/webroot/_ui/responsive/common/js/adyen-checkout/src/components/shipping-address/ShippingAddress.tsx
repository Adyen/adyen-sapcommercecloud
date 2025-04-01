import React from "react";
import {connect} from "react-redux";
import {AppState} from "../../reducers/rootReducer";
import {StoreDispatch} from "../../store/store";
import {AddressService} from "../../service/addressService";
import {AddressModel} from "../../reducers/types";
import {ShippingAddressHeader} from "../headers/ShippingAddressHeader";
import {Navigate} from "react-router-dom";
import {routes} from "../../router/routes";
import {translationsStore} from "../../store/translationsStore";
import AddressSection from "../common/AddressSection";


interface StoreProps {
    shippingAddress: AddressModel
}

interface DispatchProps {
    setFirstName: (firstName: string) => void
    setLastName: (lastName: string) => void
    setCountryCode: (countryCode: string) => void
    setRegionCode: (regionCode: string) => void
    setTitleCode: (titleCode: string) => void
    setLine1: (line1: string) => void
    setLine2: (line2: string) => void
    setCity: (city: string) => void
    setPostCode: (postCode: string) => void
    setPhoneNumber: (phoneNumber: string) => void
    setSelectedAddress: (address: AddressModel) => void
}

type ShippingAddressProps = StoreProps & DispatchProps;

interface ShippingAddressState {
    saveInAddressBook: boolean
    redirectToNextStep: boolean
    errorFieldCodes: string[]
}

class ShippingAddress extends React.Component<ShippingAddressProps, ShippingAddressState> {

    constructor(props: ShippingAddressProps) {
        super(props);
        this.state = {
            saveInAddressBook: false,
            redirectToNextStep: false,
            errorFieldCodes: []
        }
    }


    private onChangeSaveInAddressBook(value: boolean) {
        this.setState({saveInAddressBook: value})
    }

    private async handleSubmitButton() {
        let addAddressResponse = await AddressService.addDeliveryAddress(this.props.shippingAddress, this.state.saveInAddressBook, true, false, false);
        if (addAddressResponse.success) {
            this.setState({redirectToNextStep: true})
        }
        this.setState({errorFieldCodes: addAddressResponse.errorFieldCodes})
    }

    private async onSelectAddress(address: AddressModel) {
        let success = await AddressService.selectDeliveryAddress(address.id);
        if (success) {
            this.props.setSelectedAddress(address)
            this.setState({redirectToNextStep: true})
        }

    }

    render() {
        if (this.state.redirectToNextStep) {
            return <Navigate to={routes.shippingMethod}/>
        }

        return (
            <>
                <ShippingAddressHeader isActive={true}/>

                <div className={"step-body"}>

                    <div className={"shippingAddress_form checkout-shipping"}>
                        <div className={"checkout-indent"}>
                            <div
                                className={"shippingAddress_form_header headline"}>{translationsStore.get("checkout.summary.shippingAddress")}</div>
                            <AddressSection address={this.props.shippingAddress}
                                            saveInAddressBook={this.state.saveInAddressBook}
                                            errorFieldCodes={this.state.errorFieldCodes}
                                            errorFieldCodePrefix=""
                                            onCountryCodeChange={(countryCode) => this.props.setCountryCode(countryCode)}
                                            onRegionCodeChange={(regionCode) => this.props.setRegionCode(regionCode)}
                                            onTitleCodeChange={(titleCode) => this.props.setTitleCode(titleCode)}
                                            onFirstNameChange={(firstName) => this.props.setFirstName(firstName)}
                                            onLastNameChange={(lastName) => this.props.setLastName(lastName)}
                                            onLine1Change={(line1) => this.props.setLine1(line1)}
                                            onLine2Change={(line2) => this.props.setLine2(line2)}
                                            onCityChange={(city) => this.props.setCity(city)}
                                            onPostCodeChange={(postCode) => this.props.setPostCode(postCode)}
                                            onPhoneNumberChange={(phoneNumber) => this.props.setPhoneNumber(phoneNumber)}
                                            onChangeSaveInAddressBook={(saveInAddressBook) => this.onChangeSaveInAddressBook(saveInAddressBook)}
                                            onSelectAddress={(address) => this.onSelectAddress(address)}/>
                        </div>
                    </div>
                    <button className={"btn btn-primary btn-block checkout-next"}
                            onClick={() => this.handleSubmitButton()}>{translationsStore.get("checkout.multi.deliveryAddress.continue")}
                    </button>
                </div>
            </>
        )
    }
}

const mapStateToProps = (state: AppState): StoreProps => ({
    shippingAddress: state.shippingAddress
})

const mapDispatchToProps = (dispatch: StoreDispatch): DispatchProps => ({
    setFirstName: (firstName: string) => dispatch({type: "shippingAddress/setFirstName", payload: firstName}),
    setLastName: (lastName: string) => dispatch({type: "shippingAddress/setLastName", payload: lastName}),
    setCountryCode: (country: string) => dispatch({type: "shippingAddress/setCountryCode", payload: country}),
    setRegionCode: (regionCode: string) => dispatch({type: "shippingAddress/setRegionCode", payload: regionCode}),
    setTitleCode: (title: string) => dispatch({type: "shippingAddress/setTitleCode", payload: title}),
    setLine1: (line1: string) => dispatch({type: "shippingAddress/setLine1", payload: line1}),
    setLine2: (line2: string) => dispatch({type: "shippingAddress/setLine2", payload: line2}),
    setCity: (city: string) => dispatch({type: "shippingAddress/setCity", payload: city}),
    setPostCode: (postCode: string) => dispatch({type: "shippingAddress/setPostCode", payload: postCode}),
    setPhoneNumber: (phoneNumber: string) => dispatch({type: "shippingAddress/setPhoneNumber", payload: phoneNumber}),
    setSelectedAddress: (address: AddressModel) => dispatch({type: "shippingAddress/setAddress", payload: address})
})

export default connect(mapStateToProps, mapDispatchToProps)(ShippingAddress)