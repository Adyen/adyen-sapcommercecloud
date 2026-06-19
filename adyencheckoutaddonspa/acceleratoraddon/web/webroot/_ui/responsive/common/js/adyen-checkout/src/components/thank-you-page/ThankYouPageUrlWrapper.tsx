import {useParams} from "react-router-dom";
import React from "react";
import {ThankYouPage} from "./ThankYouPage";

export function ThankYouPageUrlWrapper() {
    const {orderCode} = useParams();
    const rootElement = document.getElementById("root");
    const adyenPaymentLinkUrl = rootElement?.getAttribute("adyen-payment-link-url") || undefined;

    return <ThankYouPage orderCode={orderCode || ""} adyenPaymentLinkUrl={adyenPaymentLinkUrl}/>
}