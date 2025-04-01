import React from "react";
import {translationsStore} from "../../store/translationsStore";
import {NotificationType} from "../../types/notificationType";

interface Props {
    code: string
    type: NotificationType
}

export class NotificationBar extends React.Component<Props, null> {

    getClass(): string {
        if (this.props.type === "error") {
            return "alert-danger"
        }
        if (this.props.type === "warn") {
            return "alert-warning"
        }
        return ""
    }

    render() {
        return (
            <div className={"notification-bar alert " + this.getClass()}>
                <div className="notification-bar_text">
                    {translationsStore.get(this.props.code)}
                </div>
            </div>
        )
    }
}