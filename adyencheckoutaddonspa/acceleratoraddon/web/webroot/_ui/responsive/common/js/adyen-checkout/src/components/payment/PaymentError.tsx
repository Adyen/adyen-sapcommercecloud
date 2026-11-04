import React from "react";
import {translationsStore} from "../../store/translationsStore";
import {urlContextPath} from "../../util/baseUrlUtil";

interface Props {
    errorCode: string,
    // Storefront-relative path from the error response (no context path); rendered as a link when present
    linkPath?: string
}

// Only the storefront's own paths are rendered as links, never an absolute or protocol-relative URL
const isStorefrontPath = (path?: string): boolean => !!path && path.startsWith("/") && !path.startsWith("//");

export class PaymentError extends React.Component<Props, null> {

    render() {
        const linkPath = this.props.linkPath;
        return (
            <div className="payment-error alert alert-danger">
                <div className="payment-error_text">
                    {translationsStore.get(this.props.errorCode)}
                    {isStorefrontPath(linkPath) && (
                        <>
                            {" "}
                            <a className="payment-error_link" href={urlContextPath + linkPath}>
                                {translationsStore.get("checkout.error.link.mySubscriptions")}
                            </a>
                        </>
                    )}
                </div>
            </div>
        )
    }
}
