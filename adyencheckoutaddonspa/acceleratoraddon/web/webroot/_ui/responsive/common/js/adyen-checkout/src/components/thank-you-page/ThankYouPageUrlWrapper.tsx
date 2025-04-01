import {useParams} from "react-router-dom";
import React from "react";
import {ThankYouPage} from "./ThankYouPage";

export function ThankYouPageUrlWrapper() {
    const {orderCode} = useParams();

    return <ThankYouPage orderCode={orderCode}/>
}