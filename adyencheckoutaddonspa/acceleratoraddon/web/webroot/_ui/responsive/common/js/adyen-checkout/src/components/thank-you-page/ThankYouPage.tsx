import React from "react";
import {translationsStore} from "../../store/translationsStore";
import {PaymentPending} from "./PaymentPending";
import {PaymentStatus} from "../../types/paymentStatus";
import {PaymentSuccess} from "./PaymentSuccess";
import {PaymentFailed} from "./PaymentFailed";
import {PaymentRejected} from "./PaymentRejected";
import {PaymentTimeout} from "./PaymentTimeout";
import {PaymentStatusService} from "../../service/paymentStatusService";
import {isEmpty, isNotEmpty} from "../../util/stringUtil";
import {isGuid} from "../../util/guidUtil";

interface Props {
    orderCode: string;
    adyenPaymentLinkUrl?: string;
}

interface State {
    numberOfStatusChecks: number;
    paymentStatus: PaymentStatus;
    displayedOrderCode: string;
    retryRemainingSeconds: number;
}

export class ThankYouPage extends React.Component<Props, State> {
    private statusRequestInterval = 2 * 1000;
    private numberOfAllStatusChecks = 60;
    private timer: NodeJS.Timeout;
    private retryTimer?: NodeJS.Timeout;
    private readonly retryTimeoutSeconds = 600; // 10 min

    constructor(props: Props) {
        super(props);
        this.state = {
            numberOfStatusChecks: 0,
            paymentStatus: "waiting",
            displayedOrderCode: "",
            retryRemainingSeconds: this.retryTimeoutSeconds
        };
    }

    async componentDidMount() {
        this.timer = setInterval(() => this.checkStatus(), this.statusRequestInterval);

        if (isGuid(this.props.orderCode)) {
            const orderCode = await PaymentStatusService.fetchOrderCodeForGUID(this.props.orderCode);
            this.setState((state): State => ({...state, displayedOrderCode: orderCode}));
        } else {
            this.setState((state): State => ({...state, displayedOrderCode: this.props.orderCode}));
        }
    }

    componentWillUnmount() {
        clearInterval(this.timer);
        if (this.retryTimer) {
            clearInterval(this.retryTimer);
        }
    }

    private startRetryCountdownIfNeeded() {
        if (this.retryTimer || isEmpty(this.props.adyenPaymentLinkUrl)) {
            return;
        }

        this.retryTimer = setInterval(() => {
            this.setState((prevState) => {
                const next = prevState.retryRemainingSeconds - 1;
                if (next <= 0) {
                    if (this.retryTimer) {
                        clearInterval(this.retryTimer);
                    }
                    return {...prevState, retryRemainingSeconds: 0};
                }
                return {...prevState, retryRemainingSeconds: next};
            });
        }, 1000);
    }

    private formatRetryTime(totalSeconds: number): string {
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes}:${seconds < 10 ? "0" : ""}${seconds}`;
    }

    private renderRetryButton(): React.JSX.Element | null {
        const {adyenPaymentLinkUrl} = this.props;
        const {retryRemainingSeconds} = this.state;

        if (isEmpty(adyenPaymentLinkUrl) || retryRemainingSeconds <= 0) {
            return null;
        }

        return (
            <div id="adyen-retry-alert" className="alert alert-warning" role="alert">
                <a
                    id="adyen-retry-button"
                    href={adyenPaymentLinkUrl}
                    className="btn btn-primary btn-xs"
                    style={{marginLeft: "12px"}}
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    {translationsStore.get("checkout.payment.retry") || "Pay again"}
                </a>
                <span id="adyen-retry-timer" style={{marginLeft: "8px"}}>
                    {`Link expires in ${this.formatRetryTime(retryRemainingSeconds)}`}
                </span>
            </div>
        );
    }

    private async checkStatus() {
        if (this.state.numberOfStatusChecks + 1 <= this.numberOfAllStatusChecks) {
            const paymentStatus = await PaymentStatusService.fetchPaymentStatus(this.props.orderCode);

            if (isNotEmpty(paymentStatus)) {
                this.setState((state): State => ({...state, paymentStatus: paymentStatus}));

                if (paymentStatus === "rejected") {
                    this.startRetryCountdownIfNeeded();
                }

                if (paymentStatus !== "waiting") {
                    clearInterval(this.timer);
                }
            }

            this.setState((state): State => ({
                ...state,
                numberOfStatusChecks: state.numberOfStatusChecks + 1
            }));
        } else {
            this.setState({paymentStatus: "timeout"});
            clearInterval(this.timer);
        }
    }

    private renderPaymentStatus(): React.JSX.Element {
        switch (this.state.paymentStatus) {
            case "waiting":
                return <PaymentPending/>;
            case "completed":
                return <PaymentSuccess/>;
            case "error":
                return <PaymentFailed/>;
            case "rejected":
                return (
                    <div>
                        <PaymentRejected/>
                        {this.renderRetryButton()}
                    </div>
                );
            case "timeout":
            case "unknown":
                return <PaymentTimeout/>;
        }
    }

    private renderOrderNumberSection(): React.JSX.Element {
        if (isEmpty(this.state.displayedOrderCode)) {
            return <></>;
        }
        return (
            <p>
                {translationsStore.get("text.account.order.orderNumberLabel")}
                <strong> {this.state.displayedOrderCode}</strong>
            </p>
        );
    }

    render() {
        return (
            <div className="checkout-success">
                <div className="checkout-success__body">
                    <div className="checkout-success__body__headline">
                        {translationsStore.get("checkout.orderConfirmation.thankYouForOrder")}
                    </div>
                    {this.renderOrderNumberSection()}
                    {this.renderPaymentStatus()}
                </div>
            </div>
        );
    }
}