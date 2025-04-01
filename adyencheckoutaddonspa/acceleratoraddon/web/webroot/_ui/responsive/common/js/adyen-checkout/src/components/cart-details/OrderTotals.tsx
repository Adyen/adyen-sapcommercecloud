import {CartData} from "../../types/cartData";
import React from "react";
import {Price} from "../common/Price";
import {formatStringWithPlaceholders} from "../../util/stringUtil";
import {translationsStore} from "../../store/translationsStore";

interface Props {
    cartData: CartData
    showTax: boolean
}

export class OrderTotals extends React.Component<Props, null> {

    private renderDiscounts(): React.JSX.Element {
        if (this.props.cartData.totalDiscounts && (this.props.cartData.totalDiscounts.value > 0)) {
            return (
                <div className="subtotals__item--state-discount">
                    {translationsStore.get("text.account.order.discount")}
                    <span>
                        <Price price={this.props.cartData.totalDiscounts} displayNegativeForDiscount={true}/>
                    </span>
                </div>
            )
        }

        return <></>
    }

    private renderDelivery(): React.JSX.Element {
        if (this.props.cartData.deliveryCost && (this.props.cartData.deliveryCost.value > 0)) {
            return (
                <div className="shipping">
                    {translationsStore.get("basket.page.totals.delivery")}
                    <span>
                        <Price price={this.props.cartData.deliveryCost} displayFreeForZero={true}/>
                    </span>
                </div>
            )
        }

        return <></>
    }

    private renderTax(): React.JSX.Element {
        if (this.props.cartData.totalTax && this.props.showTax && this.props.cartData.net
            && this.props.cartData.totalTax.value > 0) {
            return (
                <div className="tax">
                    {translationsStore.get("basket.page.totals.netTax")}
                    <span>
                        <Price price={this.props.cartData.totalTax}/>
			        </span>
                </div>
            )
        }

        return <></>
    }

    private renderOrderTotal(): React.JSX.Element {
        if (this.props.showTax) {
            if (this.props.cartData.totalPriceWithTax) {
                return <Price price={this.props.cartData.totalPriceWithTax}/>
            }
        } else {
            if (this.props.cartData.totalPrice) {
                return <Price price={this.props.cartData.totalPrice}/>
            }
        }
        return <> </>
    }

    private renderRealTotals(): React.JSX.Element {
        if (!this.props.cartData.net && this.props.cartData.totalTax) {
            return (
                <div className="realTotals">
                    <p>
                        {formatStringWithPlaceholders(translationsStore.get("basket.page.totals.grossTax"), this.props.cartData.totalTax.formattedValue)}
                    </p>
                </div>
            )
        }

        if (this.props.cartData.net && !this.props.showTax) {
            return (
                <div className="realTotals">
                    <p>
                        {translationsStore.get("basket.page.totals.noNetTax")}
                    </p>
                </div>
            )
        }

        return <></>
    }

    private shouldRenderEmpty() {
        return !this.props.cartData || !this.props.cartData.subTotal
    }

    render() {
        if (this.shouldRenderEmpty()) {
            return <></>
        }

        return (
            <div className="subtotals">
                <div className="subtotal">
                    {translationsStore.get("basket.page.totals.subtotal")}
                    <span>
                        <Price price={this.props.cartData.subTotal}/>
            </span>
                </div>
                {this.renderDiscounts()}
                {this.renderDelivery()}
                {this.renderTax()}
                <div className="totals">
                    {translationsStore.get("basket.page.totals.total")}
                    <span>
                        {this.renderOrderTotal()}
                    </span>
                </div>
                {this.renderRealTotals()}
            </div>
        )

    }
}