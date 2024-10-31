import React, {RefObject} from "react";
import {PaymentHeader} from "../headers/PaymentHeader";
import {connect} from "react-redux";
import {ShippingAddressHeading} from "../common/ShippingAddressHeading";
import {AddressModel} from "../../reducers/types";
import {AppState} from "../../reducers/rootReducer";
import {StoreDispatch} from "../../store/store";
import {AddressData} from "../../types/addressData";
import {InputCheckbox} from "../controls/InputCheckbox";
import {AddressService} from "../../service/addressService";
import {AdyenConfigService} from "../../service/adyenConfigService";
import { AdyenCheckout, Dropin,AdyenCheckoutError, ICore } from '@adyen/adyen-web/auto'
import '@adyen/adyen-web/styles/adyen.css';
import {AdyenConfigData} from "../../types/adyenConfigData";
import {isEmpty, isNotEmpty} from "../../util/stringUtil";
import {PlaceOrderRequest} from "../../types/paymentForm";
import {PaymentService, PlaceOrderResponse} from "../../service/paymentService";
import {translationsStore} from "../../store/translationsStore";
import AddressSection from "../common/AddressSection";
import {routes} from "../../router/routes";
import {Navigate} from "react-router-dom";
import {PaymentError} from "./PaymentError";
import {ScrollHere} from "../common/ScrollTo";
import {CoreConfiguration,CardConfiguration, UIElement} from "@adyen/adyen-web";

interface State {
    useDifferentBillingAddress: boolean
    redirectToNextStep: boolean
    executeAction: boolean
    saveInAddressBook: boolean
    errorCode: string
    errorFieldCodes: string[]
    orderNumber: string
}

interface ComponentProps {
    errorCode?: string
}

interface StoreProps {
    billingAddress: AddressModel,
    shippingAddressFromCart: AddressData,
    adyenConfig: AdyenConfigData
}

interface DispatchProps {
    setFirstName: (firstName: string) => void
    setLastName: (lastName: string) => void
    setCountryCode: (countryCode: string) => void
    setRegionCode: (countryCode: string) => void
    setTitleCode: (titleCode: string) => void
    setLine1: (line1: string) => void
    setLine2: (line2: string) => void
    setCity: (city: string) => void
    setPostCode: (postCode: string) => void
    setPhoneNumber: (phoneNumber: string) => void
    setSelectedAddress: (address: AddressModel) => void
}

type Props = StoreProps & DispatchProps & ComponentProps

class Payment extends React.Component<Props, State> {

    paymentRef: RefObject<HTMLDivElement>
    threeDSRef: RefObject<HTMLDivElement>
    dropIn: Dropin

    constructor(props: Props) {
        super(props);
        this.state = {
            useDifferentBillingAddress: false,
            redirectToNextStep: false,
            executeAction: false,
            saveInAddressBook: false,
            errorCode: this.props.errorCode ? this.props.errorCode : "",
            errorFieldCodes: [],
            orderNumber: ""
        }
        this.paymentRef = React.createRef();
        this.threeDSRef = React.createRef();
    }

    async componentDidUpdate(prevProps: Readonly<Props>, prevState: Readonly<State>, snapshot?: any) {
        if (isEmpty(prevProps.adyenConfig.adyenClientKey) && isNotEmpty(this.props.adyenConfig.adyenClientKey)) {
            await this.initializeWebComponentsCheckout()
        }
    }

    async componentDidMount() {
        AddressService.fetchAddressConfig();
        AdyenConfigService.fetchPaymentMethodsConfig();
        if (isNotEmpty(this.props.adyenConfig.adyenClientKey)) {
            await this.initializeWebComponentsCheckout()
        }
    }

    private async initializeWebComponentsCheckout() {
        let adyenCheckout = await AdyenCheckout(this.getAdyenCheckoutConfig());

        this.initiateDropIn(adyenCheckout)
    }

    private getAdyenCheckoutConfig(): CoreConfiguration {
        return {
            paymentMethodsResponse: {
                paymentMethods: this.props.adyenConfig.paymentMethods,
                storedPaymentMethods: this.props.adyenConfig.storedPaymentMethodList
            },
            locale: this.props.adyenConfig.shopperLocale,
            environment: this.castToEnvironment(this.props.adyenConfig.environmentMode),
            clientKey: this.props.adyenConfig.adyenClientKey,
            session: {
                id: this.props.adyenConfig.sessionData.id,
                sessionData: this.props.adyenConfig.sessionData.sessionData
            },
            analytics: {
                enabled: false
            },
            // @ts-ignore
            risk: {
                enabled: true
            },
            onError: (error: AdyenCheckoutError, element?: UIElement) => {
                this.handleError()
            },
            onSubmit: (state: any, element: UIElement) => this.handlePayment(state.data),
            onAdditionalDetails: (state: any, element?: UIElement) => this.handleAdditionalDetails(state.data)
        }
    }

    private getAdyenCardConfig(): CardConfiguration {
        return {
            type: 'card',
            hasHolderName: true,
            holderNameRequired: this.props.adyenConfig.cardHolderNameRequired,
            enableStoreDetails: this.props.adyenConfig.showRememberTheseDetails
        }
    }

    private castToEnvironment(env: string): CoreConfiguration['environment'] {
        const validEnvironments: CoreConfiguration['environment'][] = ['test', 'live', 'live-us', 'live-au', 'live-apse', 'live-in'];
        if (validEnvironments.includes(env as CoreConfiguration['environment'])) {
            return env as CoreConfiguration['environment'];
        }
        throw new Error(`Invalid environment: ${env}`);
    }

    private initiateDropIn(adyenCheckout: ICore) {

        this.dropIn = new Dropin(adyenCheckout, {
             paymentMethodsConfiguration: {
                    card: this.getAdyenCardConfig()
            }
        }).mount(this.paymentRef.current);

    }

    private async handleError(){
        await PaymentService.sendPaymentCancel();
        this.resetDropInComponent();
    }

    private async handlePayment(data: any) {
        let adyenPaymentForm = PaymentService.preparePlaceOrderRequest(data,
            this.state.useDifferentBillingAddress, this.isSaveInAddressBook(), this.props.billingAddress);

        await this.executePaymentRequest(adyenPaymentForm)
    }

    private async handleAdditionalDetails(data: any) {
        await this.executeAdditionalDetails(data)
    }

    private isSaveInAddressBook(): boolean {
        return this.state.saveInAddressBook && this.state.useDifferentBillingAddress
    }

    private async handleResponse(response: Promise<void | PlaceOrderResponse>) {
        this.setState({errorFieldCodes: []})

        let responseData = await response;
        if (!!responseData) {
            if (responseData.success) {
                if (responseData.executeAction) {
                    this.dropIn.handleAction(responseData.paymentsAction)
                } else {
                    this.setState({orderNumber: responseData.orderNumber})
                    this.setState({redirectToNextStep: true})
                }
            } else {
                this.setState({errorFieldCodes: responseData.errorFieldCodes})
                this.resetDropInComponent()
            }
            this.setState({errorCode: responseData.error})
        }
    }

    private async executePaymentRequest(adyenPaymentForm: PlaceOrderRequest) {
        await this.handleResponse(PaymentService.placeOrder(adyenPaymentForm));
    }

    private async executeAdditionalDetails(details: any) {
        await this.handleResponse(PaymentService.sendAdditionalDetails(details));
    }

    private resetDropInComponent() {
        this.dropIn.unmount();
        this.dropIn.mount(this.paymentRef.current)
    }

    private renderScrollOnErrorCodes(): React.JSX.Element {
        if (this.state.errorFieldCodes && this.state.errorFieldCodes.length > 0) {
            return <ScrollHere/>
        }
        return <></>
    }

    private renderBillingAddressForm(): React.JSX.Element {
        if (this.state.useDifferentBillingAddress) {
            return (
                <>
                    <hr/>
                    {this.renderScrollOnErrorCodes()}
                    <div className={"headline"}>Billing Address</div>
                    <AddressSection address={this.props.billingAddress}
                                    saveInAddressBook={this.state.saveInAddressBook}
                                    errorFieldCodes={this.state.errorFieldCodes}
                                    errorFieldCodePrefix={"billingAddress."}
                                    onCountryCodeChange={(countryCode) => this.props.setCountryCode(countryCode)}
                                    onRegionCodeChange={(regionCode) => this.props.setRegionCode(regionCode)}
                                    onTitleCodeChange={(titleCode) => this.props.setTitleCode(titleCode)}
                                    onFirstNameChange={(firstName) => this.props.setFirstName(firstName)}
                                    onLastNameChange={(lastName) => this.props.setLastName(lastName)}
                                    onLine1Change={(line1) => this.props.setLine1(line1)}
                                    onLine2Change={(line2) => this.props.setLine2(line2)}
                                    onCityChange={(city) => this.props.setCity(city)}
                                    onPostCodeChange={(postCode) => this.props.setPostCode(postCode)}
                                    onPhoneNumberChange={(phoneNumber) => this.props.setPhoneNumber(phoneNumber)}
                                    onChangeSaveInAddressBook={(saveInAddressBook) => this.onChangeSaveInAddressBook(saveInAddressBook)}
                                    onSelectAddress={(address) => this.props.setSelectedAddress(address)}
                    />
                    <hr/>
                </>
            )
        }
        return <></>
    }

    private onChangeSaveInAddressBook(value: boolean) {
        this.setState({saveInAddressBook: value})
    }

    private onChangeUseDifferentBillingAddress(value: boolean): void {
        this.setState({useDifferentBillingAddress: value})
    }

    private renderErrorMessage(): React.JSX.Element {
        if (isNotEmpty(this.state.errorCode)) {
            return <>
                <ScrollHere/>
                <PaymentError errorCode={this.state.errorCode}/>
            </>
        }
        return <></>
    }

    private getThankYouPageURL(): string {
        return routes.thankYouPage + "/" + this.state.orderNumber
    }

    render() {
        if (this.state.redirectToNextStep && isNotEmpty(this.state.orderNumber)) {
            return <Navigate to={this.getThankYouPageURL()}/>
        }

        return (
            <>
                <PaymentHeader isActive={true}/>
                <div className={"step-body"}>

                    <div className={"checkout-paymentmethod"}>
                        <ShippingAddressHeading address={this.props.shippingAddressFromCart}/>
                        <InputCheckbox
                            fieldName={translationsStore.get("checkout.multi.payment.useDifferentBillingAddress")}
                            onChange={(checkboxState) => this.onChangeUseDifferentBillingAddress(checkboxState)}
                            checked={this.state.useDifferentBillingAddress}/>
                        {this.renderBillingAddressForm()}

                        {this.renderErrorMessage()}
                        <div className={"dropin-payment"} ref={this.paymentRef}/>
                    </div>
                </div>
                <div ref={this.threeDSRef}/>
            </>
        )
    }
}

function mapDispatchToProps(dispatch: StoreDispatch): DispatchProps {
    return {
        setFirstName: (firstName: string) => dispatch({
            type: "billingAddress/setFirstName",
            payload: firstName
        }),
        setLastName: (lastName: string) => dispatch({type: "billingAddress/setLastName", payload: lastName}),
        setCountryCode: (country: string) => dispatch({
            type: "billingAddress/setCountryCode",
            payload: country
        }),
        setRegionCode: (country: string) => dispatch({
            type: "billingAddress/setRegionCode",
            payload: country
        }),
        setTitleCode: (title: string) => dispatch({type: "billingAddress/setTitleCode", payload: title}),
        setLine1: (line1: string) => dispatch({type: "billingAddress/setLine1", payload: line1}),
        setLine2: (line2: string) => dispatch({type: "billingAddress/setLine2", payload: line2}),
        setCity: (city: string) => dispatch({type: "billingAddress/setCity", payload: city}),
        setPostCode: (postCode: string) => dispatch({type: "billingAddress/setPostCode", payload: postCode}),
        setPhoneNumber: (phoneNumber: string) => dispatch({
            type: "billingAddress/setPhoneNumber",
            payload: phoneNumber
        }),
        setSelectedAddress: (address: AddressModel) => dispatch({type: "billingAddress/setAddress", payload: address})
    }
}

function mapStateToProps(state: AppState): StoreProps {
    return {
        billingAddress: state.billingAddress,
        shippingAddressFromCart: state.cartData.deliveryAddress,
        adyenConfig: state.adyenConfig
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(Payment)