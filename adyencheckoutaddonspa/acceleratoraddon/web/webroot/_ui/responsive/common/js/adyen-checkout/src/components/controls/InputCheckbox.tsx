import React from "react";
import {isNotEmpty} from "../../util/stringUtil";


interface InputCheckboxProps {
    testId?: string
    fieldName: string
    checked?: boolean
    onChange?: (value: boolean) => void
}

export class InputCheckbox extends React.Component<InputCheckboxProps, null> {

    private renderInput(): React.JSX.Element {
        if (isNotEmpty(this.props.testId)) {
            return <input id={this.props.testId}
                          className={"form-group_checkbox_label_input"} type={"checkbox"}
                          onChange={(event) => this.props.onChange ? this.props.onChange(event.target.checked) : ""}
                          checked={this.props.checked}/>
        }
        return <input className={"form-group_checkbox_label_input"} type={"checkbox"}
                      onChange={(event) => this.props.onChange ? this.props.onChange(event.target.checked) : ""}
                      checked={this.props.checked}/>

    }

    render() {
        return (
            <div className={"form-group"}>
                <div className={"checkbox"}>
                    <label className={"form-group_checkbox_label"}>
                        {this.renderInput()}
                        {this.props.fieldName}
                    </label>
                </div>
            </div>
        )
    }
}