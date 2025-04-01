import React from "react";
import {CartData, OrderEntryData} from "../../types/cartData";
import {Price} from "../common/Price";
import {urlContextPath} from "../../util/baseUrlUtil";
import {PromotionService} from "../../service/promotionService";
import HTMLReactParser from "html-react-parser";
import {translationsStore} from "../../store/translationsStore";
import {MissingImage} from "../../icon/MissingImage";

interface Props {
    entryData: OrderEntryData
    cartData: CartData
}

export class CartDetailsProduct extends React.Component<Props, null> {

    private getPrimaryImage(): React.JSX.Element {
        if(this.props.entryData.product.images!==null){
            let filteredImages = this.props.entryData.product.images.filter(img => img.imageType === "PRIMARY");
            if (filteredImages.length > 0) {
                return <img src={filteredImages[0].url} alt={filteredImages[0].altText}/>
            }
        }
        return <MissingImage/>
    }

    private getProductUrl(): string {
        return urlContextPath + this.props.entryData.product.url
    }

    private renderAppliedPromotions(): React.JSX.Element[] {
        let result: React.JSX.Element[] = [];
        if (PromotionService.doesAppliedPromotionExistForOrderEntryOrOrderEntryGroup(this.props.cartData, this.props.entryData)) {
            for (let appliedProductPromotion of this.props.cartData.appliedProductPromotions) {
                let displayed = false;
                for (let consumedEntry of appliedProductPromotion.consumedEntries) {
                    if (!displayed && PromotionService.isConsumedByEntry(consumedEntry, this.props.entryData)) {
                        displayed = true;
                        result.push(
                            <span key={this.props.entryData.entryNumber} className={"promotion"}>{HTMLReactParser(appliedProductPromotion.description)}</span>
                        )
                    }
                }
            }
        }
        return result;
    }

    render() {
        if (this.props.entryData.deliveryPointOfService || !this.props.entryData) {
            return (
                <></>
            );
        }

        return (
            <li className="checkout-order-summary-list-items">
                <div className="thumb">
                    <a href={this.getProductUrl()}>
                        {this.getPrimaryImage()}
                    </a>
                </div>
                <div className="price"><Price price={this.props.entryData.totalPrice} displayFreeForZero={true}/></div>
                <div className="details">
                    <div className="name">
                        <a href={this.getProductUrl()}>{this.props.entryData.product.name}</a>
                    </div>
                    <div>
                        <span className="label-spacing">{translationsStore.get("order.itemPrice")}</span>
                        <Price price={this.props.entryData.basePrice} displayFreeForZero={true}/>
                    </div>
                    <div className="qty">
                        <span>{translationsStore.get("basket.page.qty")} {this.props.entryData.quantity}</span>
                    </div>
                    {this.renderAppliedPromotions()}
                </div>
            </li>
        )

    }
}