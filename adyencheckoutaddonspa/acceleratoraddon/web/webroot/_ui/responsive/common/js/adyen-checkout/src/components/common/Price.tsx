import {PriceData} from "../../types/cartData";
import React from "react";
import {translationsStore} from "../../store/translationsStore";

interface Props {
    price: PriceData
    displayNegativeForDiscount?: boolean
    displayFreeForZero?: boolean
}

export class Price extends React.Component<Props, null> {
    render() {
        if (this.props.price.value > 0) {
            if (this.props.displayNegativeForDiscount) {
                return <>-{this.props.price.formattedValue}</>
            } else {
                return <>{this.props.price.formattedValue}</>
            }
        } else {
            if (this.props.displayFreeForZero) {
                return <>{translationsStore.get("basket.page.free")}</>
            } else {
                return <>{this.props.price.formattedValue}</>
            }
        }
    }
}