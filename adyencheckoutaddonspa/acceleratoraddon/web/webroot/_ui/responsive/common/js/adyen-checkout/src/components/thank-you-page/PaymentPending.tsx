import React from "react";
import {translationsStore} from "../../store/translationsStore";
import {Spinner} from "../common/Spinner";
import "./PaymentStatus.scss"

export class PaymentPending extends React.Component<any, any> {

    render() {
        return <div className={"payment-status"}>
            <Spinner/>
            <div className={"payment-status_text"}>{translationsStore.get("checkout.error.authorization.payment.pending")}</div>
        </div>
    }
}