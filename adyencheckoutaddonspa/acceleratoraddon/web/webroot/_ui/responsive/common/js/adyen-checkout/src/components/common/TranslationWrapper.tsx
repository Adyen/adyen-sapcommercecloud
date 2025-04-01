import React from "react";
import {TranslationService} from "../../service/translationService";

interface State {
    translationsInitialized: boolean
}

export class TranslationWrapper extends React.Component<React.PropsWithChildren, State> {
    constructor(props: any) {
        super(props);
        this.state = {
            translationsInitialized: false
        }
    }

    async componentDidMount() {
        let success = await TranslationService.fetchTranslations();
        if (success) {
            this.setState({translationsInitialized: true})
        }
    }

    render() {
        if (!this.state.translationsInitialized) {
            return <></>
        }

        return <>{this.props.children}</>
    }

}