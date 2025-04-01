import React from "react";
import {CheckoutSteps} from "../../types/checkoutStepsEnum";
import {connect} from "react-redux";
import {AppState} from "../../reducers/rootReducer";
import {CartData} from "../../types/cartData";
import {Navigate} from "react-router-dom";
import {routes} from "../../router/routes";
import {StoreDispatch} from "../../store/store";
import {Notification} from "../../reducers/types";
import {createWarn} from "../../util/notificationUtil";

interface ComponentProps {
    currentCheckoutStep: CheckoutSteps
}

interface StoreProps {
    cartData: CartData,
    cartDataLoading: boolean
}

interface DispatchProps {
    addNotification: (notification: Notification) => void
}

type Props = ComponentProps & StoreProps & React.PropsWithChildren & DispatchProps;

interface State {
    redirectToShippingAddress: boolean;
    redirectToShippingMethod: boolean;
    renderChildren: boolean;
}

class RedirectOnIncompleteData extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);
        this.state = {
            redirectToShippingAddress: false,
            redirectToShippingMethod: false,
            renderChildren: false,
        }
    }

    componentDidUpdate(prevProps: Readonly<Props>, prevState: Readonly<State>, snapshot?: any) {
        if (prevProps.cartDataLoading === true && this.props.cartDataLoading === false) {
            let noRedirect = this.evaluateRedirects();
            if (noRedirect) {
                this.setState({renderChildren: true})
            }
        }
    }

    private sendRedirectToShippingAddress() {
        const notification = createWarn("checkout.deliveryAddress.notSelected", true)
        this.props.addNotification(notification)
    }

    private sendRedirectToShippingMethod() {
        const notification = createWarn("checkout.deliveryMethod.notSelected", true)
        this.props.addNotification(notification)
    }

    private evaluateRedirects() {
        let noRedirect = true;
        if (this.props.currentCheckoutStep === CheckoutSteps.SHIPPING_METHOD) {
            if (!this.props.cartData.deliveryAddress) {
                this.setState({redirectToShippingAddress: true})
                noRedirect = false;
                this.sendRedirectToShippingAddress()
            }
        }
        if (this.props.currentCheckoutStep === CheckoutSteps.PAYMENT_METHOD) {
            if (!this.props.cartData.deliveryAddress) {
                this.setState({redirectToShippingAddress: true})
                noRedirect = false;
                this.sendRedirectToShippingAddress()
            } else if (!this.props.cartData.deliveryMode) {
                this.setState({redirectToShippingMethod: true})
                noRedirect = false;
                this.sendRedirectToShippingMethod()
            }
        }
        return noRedirect;
    }

    render() {
        if (this.state.redirectToShippingAddress) {
            return <Navigate to={routes.shippingAddressRedirect}/>
        }
        if (this.state.redirectToShippingMethod) {
            return <Navigate to={routes.shippingMethodRedirect}/>
        }
        if (this.state.renderChildren) {
            return <>{this.props.children}</>
        }
        return <></>
    }
}

function mapStateToProps(appState: AppState): StoreProps {
    return {
        cartData: appState.cartData,
        cartDataLoading: appState.loadingState.loadingCartData
    }
}

function mapDispatchToProps(dispatch: StoreDispatch): DispatchProps {
    return {
        addNotification: (notification: Notification) => dispatch({
            type: "notifications/addNotification",
            payload: notification
        }),
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(RedirectOnIncompleteData)