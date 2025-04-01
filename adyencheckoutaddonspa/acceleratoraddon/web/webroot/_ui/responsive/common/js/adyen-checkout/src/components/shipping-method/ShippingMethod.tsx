import React from "react";
import {InputDropdown} from "../controls/InputDropdown";
import {ShippingMethodHeader} from "../headers/ShippingMethodHeader";
import {ShippingMethodService} from "../../service/shippingMethodService";
import {connect} from "react-redux";
import {AppState} from "../../reducers/rootReducer";
import {CodeValueItem, ShippingMethodModel} from "../../reducers/types";
import HTMLReactParser from "html-react-parser";
import {routes} from "../../router/routes";
import {Navigate} from "react-router-dom";
import {ShippingAddressHeading} from "../common/ShippingAddressHeading";
import {AddressData} from "../../types/addressData";
import {translationsStore} from "../../store/translationsStore";

interface StoreProps {
    shippingMethods: ShippingMethodModel[],
    selectedShippingMethodCode: string,
    shippingAddress: AddressData
}

type Props = StoreProps

interface State {
    redirectToNextStep: boolean
}

class ShippingMethod extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);
        this.state = {redirectToNextStep: false}
    }

    componentDidMount() {
        ShippingMethodService.fetchShippingMethods();
    }

    private getDropdownItems(): CodeValueItem[] {
        return this.props.shippingMethods.map(shippingMethod => {
            return {
                code: shippingMethod.code,
                value: `${shippingMethod.name}\xa0-\xa0${shippingMethod.description}\xa0-\xa0${shippingMethod.deliveryCost.formattedValue}`
            }
        })
    }

    private onShippingMethodChange(code: string) {
        ShippingMethodService.selectShippingMethod(code)
    }

    private async handleSubmitButton() {
        let result = await ShippingMethodService.selectShippingMethod(this.props.selectedShippingMethodCode);
        if (result) {
            this.setState({redirectToNextStep: true})
        }
    }

    render() {
        if (this.state.redirectToNextStep) {
            return <Navigate to={routes.paymentMethod}/>
        }

        return (
            <>
                <ShippingMethodHeader isActive={true}/>

                <div className={"step-body"}>

                    <div className={"checkout-shipping"}>
                        <div className="checkout-shipping-items row">
                            <div className={"col-sm-12"}>
                                <ShippingAddressHeading address={this.props.shippingAddress}/>
                            </div>
                        </div>
                        <hr/>
                        <div className={"checkout-indent"}>
                            <div
                                className={"headline"}>{translationsStore.get("checkout.summary.deliveryMode.selectDeliveryMethodForOrder")}</div>
                            <InputDropdown testId={"delivery_method"}
                                           values={this.getDropdownItems()}
                                           onChange={(code) => {
                                               this.onShippingMethodChange(code)
                                           }}
                                           selectedValue={this.props.selectedShippingMethodCode}/>
                        </div>
                        <p>{HTMLReactParser(translationsStore.get("checkout.multi.deliveryMethod.message"))}</p>
                    </div>
                    <button id="deliveryMethodSubmit"
                            className={"btn btn-primary btn-block checkout-next"}
                            onClick={() => this.handleSubmitButton()}>{translationsStore.get("checkout.multi.deliveryMethod.continue")}
                    </button>
                </div>
            </>
        )
    }
}

const mapStateToProps = (state: AppState): StoreProps => ({
    shippingMethods: state.shippingMethod.shippingMethods,
    selectedShippingMethodCode: state.shippingMethod.selectedShippingMethodCode,
    shippingAddress: state.cartData.deliveryAddress
})

export default connect(mapStateToProps)(ShippingMethod)