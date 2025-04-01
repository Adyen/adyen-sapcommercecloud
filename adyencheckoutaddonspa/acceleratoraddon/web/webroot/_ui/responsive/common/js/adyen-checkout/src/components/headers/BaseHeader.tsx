import React from "react";
import {Link} from "react-router-dom";

export interface BaseHeaderProps {
    isActive?: boolean;
    editEnabled?: boolean;
}

export class BaseHeader extends React.Component<BaseHeaderProps, null> {
    private readonly title: string;
    private readonly redirectUrl: string;

    constructor(props: BaseHeaderProps, title: string, redirectUrl: string) {
        super(props);
        this.title = title;
        this.redirectUrl = redirectUrl;
    }

    private getClass(): string {
        if (this.props.isActive) {
            return "step-head active"
        }
        return "step-head"
    }

    private renderEdit(): React.JSX.Element {
        if (this.props.editEnabled) {
            return (
                <div className="edit">
                    <span className="glyphicon glyphicon-pencil"></span>
                </div>
            )
        }
        return <></>
    }

    render() {
        return (
            <Link to={this.redirectUrl} className={this.getClass()}>
                <div className={"title"}>
                    {this.title}
                </div>
                {this.renderEdit()}
            </Link>
        )
    }
}