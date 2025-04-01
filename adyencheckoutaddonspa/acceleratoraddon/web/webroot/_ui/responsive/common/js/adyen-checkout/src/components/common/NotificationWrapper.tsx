import React from "react";
import {NotificationBar} from "./NotificationBar";
import {connect} from "react-redux";
import {AppState} from "../../reducers/rootReducer";
import {ScrollHere} from "./ScrollTo";
import {Notification} from "../../reducers/types";
import {CheckoutSteps} from "../../types/checkoutStepsEnum";
import {StoreDispatch} from "../../store/store";

interface DispatchProps {
    removeAllNotifications: () => void,
    removeNotificationsWithoutRedirect: () => void
}

interface StateProps {
    notifications: Notification[],
}

interface ComponentProps {
    checkoutStep: CheckoutSteps,
    redirectOnMissingData?: boolean
}

type Props = StateProps & React.PropsWithChildren & ComponentProps & DispatchProps

class NotificationWrapper extends React.Component<Props, null> {

    private scrollKey = 1000

    componentDidUpdate(prevProps: Readonly<Props>, prevState: Readonly<null>) {
        if(prevProps.checkoutStep !== this.props.checkoutStep){
            this.props.removeNotificationsWithoutRedirect()
        }

        if(prevProps.redirectOnMissingData && !this.props.redirectOnMissingData){
            this.props.removeAllNotifications()
        }

    }

    //To force re-render scroll component
    private updateScrollKey() {
        if (this.scrollKey > 1050) {
            this.scrollKey = 1000
        } else {
            this.scrollKey++
        }
    }

    private renderNotificationBars(): React.JSX.Element[] {
        const result: React.JSX.Element[] = []
        const notificationBars = this.props.notifications.map((item, index) => (
            <NotificationBar key={index} code={item.errorResponse.errorCode} type={item.notificationType}/>
        ))
        if (notificationBars.length > 0) {
            result.push(<ScrollHere key={this.scrollKey}/>)
            this.updateScrollKey()
        }
        result.push(...notificationBars)
        return result
    }

    render() {
        return <>
            {this.renderNotificationBars()}
            {this.props.children}
        </>
    }
}

function mapStateToProps(state: AppState): StateProps {
    return {
        notifications: state.notifications
    }
}

function mapDispatchToProps(dispatch: StoreDispatch): DispatchProps {
    return {
        removeAllNotifications: () => dispatch({
            type: "notifications/removeAllNotifications"
        }),
        removeNotificationsWithoutRedirect: () => dispatch({
            type: "notifications/removeNotificationsWithoutRedirect"
        })
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(NotificationWrapper)
