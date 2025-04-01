import React from "react";
import {translationsStore} from "../../store/translationsStore";

interface Props {
    errorCode: string
}

export class PaymentError extends React.Component<Props, null> {

    render() {
        return (
            <div className="payment-error alert alert-danger">
                <div className="payment-error_text">
                    {translationsStore.get(this.props.errorCode)}
                </div>
            </div>
        )
    }
}