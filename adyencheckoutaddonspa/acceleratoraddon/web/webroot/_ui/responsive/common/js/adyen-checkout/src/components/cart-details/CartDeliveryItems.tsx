import React from "react";
import {AddressData} from "../../types/addressData";
import {isNotEmpty} from "../../util/stringUtil";
import {translationsStore} from "../../store/translationsStore";

interface Props {
    addressData: AddressData;
    hideDeliveryAddress?: boolean
}

export class CartDeliveryItems extends React.Component<Props, null> {
    render() {

        if (this.props.addressData && !this.props.hideDeliveryAddress) {
            return (
                <li className="checkout-order-summary-list-heading">
                    <div className="title">
                        {translationsStore.get("checkout.pickup.items.to.be.shipped")}
                    </div>
                    <div className="address">
                        {isNotEmpty(this.props.addressData.title) ? this.props.addressData.title + "\xa0" : ""}
                        {this.props.addressData.firstName}&nbsp;{this.props.addressData.lastName}
                        <br/>
                        {isNotEmpty(this.props.addressData.line1) ? this.props.addressData.line1 + ",\xa0" : ""}
                        {isNotEmpty(this.props.addressData.line2) ? this.props.addressData.line2 + ",\xa0" : ""}
                        {isNotEmpty(this.props.addressData.town) ? this.props.addressData.town + ",\xa0" : ""}
                        {isNotEmpty(this.props.addressData.postalCode) ? this.props.addressData.postalCode + ",\xa0" : ""}
                        {isNotEmpty(this.props.addressData.country.name) ? this.props.addressData.country.name + "\xa0" : ""}
                        <br/>
                        {isNotEmpty(this.props.addressData.phone) ? this.props.addressData.phone + "\xa0" : ""}
                    </div>
                </li>
            );
        }

        return (
            <li className="checkout-order-summary-list-heading">
                {translationsStore.get("checkout.pickup.items.to.be.delivered")}
            </li>
        )
    }
}