import React from "react";
import {translationsStore} from "../../store/translationsStore";
import {IconTimeout} from "../../icon/IconTimeout";
import "./PaymentStatus.scss"


export class PaymentTimeout extends React.Component<any, any> {

    render() {
        return <div className={"payment-status"}>
            <IconTimeout/>
            <div className={"payment-status_text"}>{translationsStore.get("checkout.error.authorization.payment.timeout")}</div>
        </div>
    }
}