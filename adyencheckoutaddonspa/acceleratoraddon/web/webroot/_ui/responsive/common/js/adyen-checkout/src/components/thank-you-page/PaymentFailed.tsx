import React from "react";
import {translationsStore} from "../../store/translationsStore";
import {IconFail} from "../../icon/IconFail";
import "./PaymentStatus.scss"

export class PaymentFailed extends React.Component<any, any> {

    render() {
        return <div className={"payment-status"}>
            <IconFail/>
            <div className={"payment-status_text"}>{translationsStore.get("checkout.error.authorization.payment.failed")}</div>
        </div>
    }
}