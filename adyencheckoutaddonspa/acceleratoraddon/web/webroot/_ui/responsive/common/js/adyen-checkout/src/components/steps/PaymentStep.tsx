import React, {useEffect} from "react";
import {ShippingMethodHeader} from "../headers/ShippingMethodHeader";
import {ShippingAddressHeader} from "../headers/ShippingAddressHeader";
import Payment from "../payment/Payment";
import RedirectOnIncompleteData from "../common/RedirectOnIncompleteData";
import {CheckoutSteps} from "../../types/checkoutStepsEnum";
import {CartDataService} from "../../service/cartDataService";
import {useParams} from "react-router-dom";

export function PaymentStep() {
    const {errorCode} = useParams();


    useEffect(() => {
        CartDataService.fetchCartData();
    })

    function getErrorCode(): string {
        return errorCode ? atob(errorCode) : undefined;
    }

    return (
        <RedirectOnIncompleteData currentCheckoutStep={CheckoutSteps.PAYMENT_METHOD}>
            <ShippingAddressHeader editEnabled={true}/>
            <ShippingMethodHeader editEnabled={true}/>
            <Payment errorCode={getErrorCode()}/>
        </RedirectOnIncompleteData>
    )
}