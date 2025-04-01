import React from "react";
import {PaymentHeader} from "../headers/PaymentHeader";
import {ShippingAddressHeader} from "../headers/ShippingAddressHeader";
import {ScrollHere} from "../common/ScrollTo";
import ShippingMethod from "../shipping-method/ShippingMethod";
import RedirectOnIncompleteData from "../common/RedirectOnIncompleteData";
import {CheckoutSteps} from "../../types/checkoutStepsEnum";
import {CartDataService} from "../../service/cartDataService";

export class ShippingMethodStep extends React.Component<{}, null> {

    componentDidMount() {
        CartDataService.fetchCartData();
    }

    render() {

        return (
            <>
                <RedirectOnIncompleteData currentCheckoutStep={CheckoutSteps.SHIPPING_METHOD}>
                    <ScrollHere/>
                    <ShippingAddressHeader editEnabled={true}/>
                    <ShippingMethod/>
                    <PaymentHeader/>
                </RedirectOnIncompleteData>
            </>
        )
    }
}