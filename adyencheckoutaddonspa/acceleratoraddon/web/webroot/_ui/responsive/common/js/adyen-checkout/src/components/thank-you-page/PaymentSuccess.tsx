import React from "react";
import {IconSuccess} from "../../icon/IconSuccess";
import {translationsStore} from "../../store/translationsStore";
import "./PaymentStatus.scss"

export class PaymentSuccess extends React.Component<any, any> {

    render() {
        return <div className={"payment-status"}>
            <div className="payment-status_icon">
                <IconSuccess/>
            </div>
            <div className={"payment-status_text"}>{translationsStore.get("checkout.error.authorization.payment.success")}</div>
        </div>
    }
}