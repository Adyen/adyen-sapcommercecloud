import React from 'react'
import './App.scss';
import CartDetails from "./components/cart-details/CartDetails";
import {translationsStore} from "./store/translationsStore";

class CheckoutStepWrapper extends React.Component<React.PropsWithChildren, null> {

    render() {
        return (
            <>
                <div className="col-sm-6">
                    <div className="checkout-headline">
                        <span className="glyphicon glyphicon-lock"></span>
                        {translationsStore.get("checkout.multi.secure.checkout")}
                    </div>
                    <div className={"checkout-steps"}>
                        {this.props.children}
                    </div>
                </div>

                <div className="col-sm-6 hidden-xs">
                    <CartDetails/>
                </div>
            </>
        );
    }
}

export default CheckoutStepWrapper;
