import React from "react";
import {isNotEmpty} from "../../util/stringUtil";
import {FieldGroup} from "./FieldGroup";


interface InputTextProps {
    testId?: string
    fieldName: string
    value?: string
    fieldErrorId?: string
    fieldErrorTextCode?: string
    fieldErrors?: string[]
    onChange: (value: string) => void
}

export class InputText extends React.Component<InputTextProps, null> {

    private hasError(): boolean {
        if (!this.props.fieldErrors) {
            return false;
        }

        return this.props.fieldErrors.includes(this.props.fieldErrorId);
    }

    private renderInput(): React.JSX.Element {
        if (isNotEmpty(this.props.testId)) {
            return <input id={this.props.testId}
                          className={"form-input_input form-control"} type={"text"}
                          onChange={(event) => this.props.onChange(event.target.value)} value={this.props.value}/>
        }

        return <input className={"form-input_input form-control"} type={"text"}
                      onChange={(event) => this.props.onChange(event.target.value)} value={this.props.value}/>

    }

    render() {
        return (
            <FieldGroup errorMessageCode={this.props.fieldErrorTextCode} hasError={this.hasError()}>
                <label className={"form-input_name control-label"}>{this.props.fieldName}</label>
                {this.renderInput()}
            </FieldGroup>

        )
    }
}