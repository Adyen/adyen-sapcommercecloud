import React from "react";
import {translationsStore} from "../../store/translationsStore";

interface ComponentProps {
    hasError: boolean
    errorMessageCode: string
}

type Props = React.PropsWithChildren & ComponentProps

export class FieldGroup extends React.Component<Props, null> {

    render() {
        if (this.props.hasError) {
            return (
                <div className={"form-group has-error"}>
                    {this.props.children}
                    <div className="help-block">
                        <span>{translationsStore.get(this.props.errorMessageCode)}</span>
                    </div>
                </div>
            )
        } else {
            return (
                <div className={"form-group"}>
                    {this.props.children}
                </div>
            )
        }
    }

}