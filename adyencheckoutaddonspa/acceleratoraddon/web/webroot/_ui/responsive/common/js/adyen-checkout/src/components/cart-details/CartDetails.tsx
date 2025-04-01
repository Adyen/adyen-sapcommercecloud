import React from "react";
import {CartDeliveryItems} from "./CartDeliveryItems";
import {connect} from "react-redux";
import {CartData} from "../../types/cartData";
import {AppState} from "../../reducers/rootReducer";
import {CartDetailsProduct} from "./CartDetailsProduct";
import {OrderTotals} from "./OrderTotals";
import {translationsStore} from "../../store/translationsStore";

interface StoreProps {
    cartData: CartData
}

class CartDetails extends React.Component<StoreProps, any> {

    // implementation needed doesPotentialPromotionExistForOrderEntryOrOrderEntryGroup doesAppliedPromotionExistForOrderEntryOrOrderEntryGroup

    // No pickup (POS support)
    // No multivariant product
    // No quotes

    private renderEntries(): React.JSX.Element[] {
        if (this.props.cartData.entries) {
            return this.props.cartData.entries.map(entry => <CartDetailsProduct key={entry.entryNumber}
                                                                                entryData={entry}
                                                                                cartData={this.props.cartData}/>)
        }
        return []
    }

    render() {
        if (this.props.cartData) {
            return (
                <>
                    <div className={"checkout-summary-headline"}>{translationsStore.get("checkout.multi.order.summary")}</div>
                    <div className={"checkout-order-summary"}>
                        <ul className="checkout-order-summary-list">
                            <CartDeliveryItems addressData={this.props.cartData.deliveryAddress}/>

                            {this.renderEntries()}
                        </ul>

                        <OrderTotals cartData={this.props.cartData} showTax={true}/>
                    </div>
                </>
            )
        }

        return (<></>)
    }
}

function mapStateToProps(appState: AppState): StoreProps {
    return {
        cartData: appState.cartData
    }
}

export default connect(mapStateToProps)(CartDetails)