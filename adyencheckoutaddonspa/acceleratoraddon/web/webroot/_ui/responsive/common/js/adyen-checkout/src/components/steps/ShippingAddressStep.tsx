import React from "react";
import ShippingAddress from "../shipping-address/ShippingAddress";
import {ShippingMethodHeader} from "../headers/ShippingMethodHeader";
import {PaymentHeader} from "../headers/PaymentHeader";
import {ScrollHere} from "../common/ScrollTo";
import {CartDataService} from "../../service/cartDataService";

export class ShippingAddressStep extends React.Component<{}, null> {
    componentDidMount() {
        CartDataService.fetchCartData();
    }

    render() {
        return (
            <>
                <ScrollHere/>
                <ShippingAddress/>
                <ShippingMethodHeader/>
                <PaymentHeader/>
            </>
        )
    }

}