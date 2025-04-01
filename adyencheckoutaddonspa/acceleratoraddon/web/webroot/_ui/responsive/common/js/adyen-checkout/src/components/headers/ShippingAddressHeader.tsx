import {BaseHeader, BaseHeaderProps} from "./BaseHeader";
import {routes} from "../../router/routes";
import {translationsStore} from "../../store/translationsStore";

const redirectUrl = routes.shippingAddress;

export class ShippingAddressHeader extends BaseHeader {

    constructor(props: BaseHeaderProps) {
        super(props, translationsStore.get("checkout.multi.deliveryAddress"), redirectUrl);
    }

}