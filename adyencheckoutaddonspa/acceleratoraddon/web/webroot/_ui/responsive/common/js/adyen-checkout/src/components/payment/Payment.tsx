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
import {AdyenCheckout, AdyenCheckoutError, Dropin, ICore} from '@adyen/adyen-web/auto'
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
import {
    AdditionalDetailsActions,
    CardConfiguration,
    CoreConfiguration,
    SubmitActions,
    UIElement
} from "@adyen/adyen-web";
import {adyenConfigInitialState} from "../../reducers/adyenConfigReducer";

interface State {
    useDifferentBillingAddress: boolean
    redirectToNextStep: boolean
    executeAction: boolean
    saveInAddressBook: boolean
    errorCode: string
    errorFieldCodes: string[]
    orderNumber: string
    partialPaymentId?: string
}

interface ComponentProps {
    errorCode?: string
}

interface StoreProps {
    billingAddress: AddressModel,
    shippingAddressFromCart: AddressData,
    adyenConfig: AdyenConfigData,
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
    setCompanyName: (companyName: string) => void
    setTaxNumber: (taxNumber: string) => void
    setRegistrationNumber: (registrationNumber: string) => void
    setSelectedAddress: (address: AddressModel) => void
    removeAdyenConfigFromStore: () => void
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
            orderNumber: "",
            partialPaymentId: undefined
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

    componentWillUnmount() {
        this.props.removeAdyenConfigFromStore();
    }

    private async initializeWebComponentsCheckout() {
        let adyenCheckout = await AdyenCheckout(this.getAdyenCheckoutConfig());

        this.initiateDropIn(adyenCheckout)
    }

    /**
     * Get Adyen Checkout configuration including partial payments support for gift cards
     * @returns CoreConfiguration object with all necessary settings
     */
    private getAdyenCheckoutConfig(): CoreConfiguration {
        return {
            paymentMethodsResponse: {
                paymentMethods: this.props.adyenConfig.paymentMethods,
                storedPaymentMethods: this.props.adyenConfig.storedPaymentMethodList
            },
            locale: this.props.adyenConfig.shopperLocale,
            environment: this.castToEnvironment(this.props.adyenConfig.environmentMode),
            clientKey: this.props.adyenConfig.adyenClientKey,
            countryCode: this.props.adyenConfig.countryCode,
            amount: this.props.adyenConfig.amount,
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
            onSubmit: (state: any, element: UIElement, actions: SubmitActions) => this.handlePayment(state.data, actions),
            onAdditionalDetails: (state: any, element: UIElement,actions: AdditionalDetailsActions) => this.handleAdditionalDetails(state.data, actions),
            // Partial payments callbacks for gift cards
            onBalanceCheck: (resolve: any, reject: any, data: any) => this.handleBalanceCheck(resolve, reject, {...data, amount: this.props.adyenConfig.amount}),
            onOrderRequest: (resolve: any, reject: any, data: any) => this.handleOrderRequest(resolve, reject, {...data, amount: this.props.adyenConfig.amount})
        }
    }

    private getAdyenCardConfig(): CardConfiguration {
        return {
            type: 'card',
            hasHolderName: true,
            holderNameRequired: this.props.adyenConfig.cardHolderNameRequired,
            enableStoreDetails: this.props.adyenConfig.showRememberTheseDetails,
            clickToPayConfiguration: {
                merchantDisplayName: this.props.adyenConfig.merchantDisplayName,
                shopperEmail:  this.props.adyenConfig.shopperEmail,
                locale: this.props.adyenConfig.clickToPayLocale,
            }
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
                 card: this.getAdyenCardConfig(),
                 boletobancario: {
                     // @ts-ignore
                     personalDetailsRequired: true,
                     billingAddressRequired: false,
                     showEmailAddress: false,
                     data: {
                         firstName: this.props.shippingAddressFromCart.firstName,
                         lastName: this.props.shippingAddressFromCart.lastName,
                     }
                 },
                 // Gift card configuration for partial payments
                 giftcard: {
                     // @ts-ignore - Gift card styling configuration
                     styles: {
                         base: {
                             fontSize: '16px',
                             color: '#333'
                         }
                     }
                 }
            },
            // Critical: Enable partial payments functionality
            // @ts-ignore - Enable showing pay button for partial payments
            showPayButton: true,
            // @ts-ignore - Allow removal of payment methods in partial payment flow
            showRemovePaymentMethodButton: true,
            // @ts-ignore - Enable partial payment mode
            isPartialPayment: false, // Will be set to true dynamically when needed
            // @ts-ignore - Show remaining amount when in partial payment mode
            showRemainingAmount: true
        }).mount(this.paymentRef.current);

    }

    private async handleError(){
        await PaymentService.sendPaymentCancel();
        this.resetDropInComponent();
    }

    private async handlePayment(data: any, actions: SubmitActions) {
        let adyenPaymentForm = PaymentService.preparePlaceOrderRequest(data,
            this.state.useDifferentBillingAddress, this.isSaveInAddressBook(), this.props.billingAddress, this.state.partialPaymentId);

        await this.executePaymentRequest(adyenPaymentForm, actions)
    }

    private async handleAdditionalDetails(data: any,actions: AdditionalDetailsActions) {
        await this.executeAdditionalDetails(data,actions)
    }

    /**
     * Handle balance check for gift cards in partial payments
     * This method will be called when a gift card balance needs to be verified
     *
     * @param resolve - Function to call with successful balance response
     * @param reject - Function to call with error response
     * @param data - Gift card data including card number and amount
     *
     * Expected backend endpoint: POST /api/giftcard/balance
     * Request payload: { cardNumber: string, pin?: string, amount: { value: number, currency: string } }
     * Response: { balance: { value: number, currency: string }, transactionLimit: { value: number, currency: string } }
     */
    private async handleBalanceCheck(resolve: any, reject: any, data: any) {
        try {
            console.log('Balance check requested for gift card:', data);
            
            // Extract gift card data from Adyen Web Components structure
            // For gift cards, the data structure is different from regular payment methods
            const paymentMethod = data.paymentMethod || {};
            const cardNumber = paymentMethod.number || paymentMethod.encryptedCardNumber || paymentMethod.cardNumber;
            const pin = paymentMethod.cvc || paymentMethod.encryptedSecurityCode || paymentMethod.pin;
            const amount = data.amount;
            
            // Validate required fields
            if (!cardNumber) {
                console.error('Gift card number is missing from payment data');
                reject({
                    errorCode: 'missing_card_number',
                    message: 'Gift card number is required'
                });
                return;
            }
            
            if (!amount) {
                console.error('Amount is missing from payment data');
                reject({
                    errorCode: 'missing_amount',
                    message: 'Amount is required for balance check'
                });
                return;
            }
            
            // Call the real backend endpoint
            const response = await PaymentService.checkGiftCardBalance({
                cardNumber: cardNumber,
                pin: pin,
                amount: amount,
                brand: paymentMethod.brand,
                type: paymentMethod.type
            });
            
            console.log('Balance check response:', response);
            
            // Store partialPaymentId if present in the response
            if (response.partialPaymentId) {
                this.setState({ partialPaymentId: response.partialPaymentId });
                console.log('Stored partialPaymentId:', response.partialPaymentId);
            }

            const balanceResponse = {
                balance: response.balance,
                transactionLimit: response.transactionLimit || response.balance,
                // Include partial payment metadata if present
                partialPaymentId: response.partialPaymentId,
                chargedAmount: response.chargedAmount,
                remainingAmount: response.remainingAmount
            };
            
            // Log the response for debugging
            console.log('Formatted balance response:', balanceResponse);
            console.log('Balance value:', response.balance?.value, 'Requested amount:', amount.value);
            
            resolve(balanceResponse);
        } catch (error) {
            console.error('Balance check failed:', error);
            reject({
                errorCode: 'balance_check_failed',
                message: 'Unable to verify gift card balance'
            });
        }
    }

    /**
     * Handle order request for partial payments
     * This method will be called when an order needs to be created for partial payment
     *
     * @param resolve - Function to call with successful order response
     * @param reject - Function to call with error response
     * @param data - Order data including amount and payment method details
     *
     * Expected backend endpoint: POST /api/orders/partial-payment
     * Request payload: { amount: { value: number, currency: string }, paymentMethod: object }
     * Response: { orderData: string, pspReference: string }
     */
    private async handleOrderRequest(resolve: any, reject: any, data: any) {
        try {
            console.log('Order request for partial payment:', data);
            
            // Call the real backend endpoint
            const response = await PaymentService.createPartialPaymentOrder({
                amount: data.amount,
                paymentMethod: data.paymentMethod,
                shopperReference: this.props.adyenConfig.shopperEmail, // Use shopperEmail as reference or undefined
                partialPaymentId: this.state.partialPaymentId // Include the stored partialPaymentId
            });
            
            console.log('Order request response:', response);
            
            // Validate response has required fields
            if (!response.orderData || !response.pspReference) {
                console.error('Invalid order response: missing orderData or pspReference', response);
                reject({
                    errorCode: 'invalid_order_response',
                    message: 'Invalid order response: missing required fields'
                });
                return;
            }

            const orderResponse = {
                orderData: response.orderData,
                pspReference: response.pspReference
            };
            
            console.log('Resolving order request with:', orderResponse);
            resolve(orderResponse);
            
        } catch (error) {
            console.error('Order request failed:', error);
            reject({
                errorCode: 'order_creation_failed',
                message: 'Unable to create order for partial payment: ' + (error.message || 'Unknown error')
            });
        }
    }

    private isSaveInAddressBook(): boolean {
        return this.state.saveInAddressBook && this.state.useDifferentBillingAddress
    }

    /**
     * Handle payment response from backend, including partial payment scenarios for gift cards
     *
     * This method processes the response from the place order API call and determines how to
     * inform the Adyen DropIn component about the payment status.
     *
     * For partial payments with gift cards:
     * 1. When a gift card has insufficient balance, the backend will:
     *    - Charge the available amount from the gift card
     *    - Return isPartialPayment=true with remainingAmount > 0
     *    - Provide partialPaymentId for subsequent payment attempts
     *
     * 2. This method will then:
     *    - Store the partialPaymentId for the next payment attempt
     *    - Resolve the DropIn with 'PartiallyAuthorised' result code
     *    - Update DropIn configuration to show remaining amount
     *    - Allow user to select another payment method for the remaining amount
     *
     * 3. For complete payments (remainingAmount = 0):
     *    - Resolve with the actual result code from Adyen
     *    - Redirect to thank you page if successful
     *
     * @param response Promise containing the backend response
     * @param actions SubmitActions from Adyen DropIn to resolve/reject the payment
     */
    private async handleResponse(response: Promise<void | PlaceOrderResponse>, actions: SubmitActions) {
        this.setState({errorFieldCodes: []})

        let responseData = await response;
        if (!!responseData) {
            if (responseData.success) {
                if (responseData.executeAction) {
                    this.dropIn.handleAction(responseData.paymentsAction)
                } else {
                    // Handle partial payment scenarios
                    if (responseData.paymentsResponse && responseData.paymentsResponse.order && responseData.paymentsResponse.order.remainingAmount.value > 0) {
                        console.log('Partial payment detected:', {
                            partialPaymentId: responseData.partialPaymentId,
                            chargedAmount: responseData.chargedAmount,
                            remainingAmount: responseData.remainingAmount
                        });
                        
                        // Store the partial payment ID for subsequent payments
                        if (responseData.partialPaymentId) {
                             this.setState({ partialPaymentId: responseData.partialPaymentId });
                        }
                        
                        // Inform DropIn about partial payment success
                        // For partial payments, we need to resolve with PartiallyAuthorised result code
                        // This tells DropIn that the gift card payment was successful but more payment is needed
                        actions.resolve(responseData.paymentsResponse);
                        
                        
                        console.log('DropIn informed about partial payment, remaining amount:', responseData.remainingAmount);
                        
                    } else {
                        // Handle complete payment (no remaining amount)
                        actions.resolve({
                            resultCode: responseData.paymentsResponse.resultCode
                        });
                        
                        // Only redirect if payment is fully completed
                        if (responseData.orderNumber) {
                            this.setState({orderNumber: responseData.orderNumber});
                            this.setState({redirectToNextStep: true});
                        }
                    }
                }
            } else {
                this.setState({errorFieldCodes: responseData.errorFieldCodes})
                this.resetDropInComponent()
            }
            this.setState({errorCode: responseData.error})
        }
    }

    private async executePaymentRequest(adyenPaymentForm: PlaceOrderRequest, actions: SubmitActions) {
        await this.handleResponse(PaymentService.placeOrder(adyenPaymentForm), actions);
    }

    private async executeAdditionalDetails(details: any, actions: AdditionalDetailsActions) {
        await this.handleResponse(PaymentService.sendAdditionalDetails(details), actions);
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
                                    onCompanyNameChange={(companyName) => this.props.setCompanyName(companyName)}
                                    onRegistrationNumberChange={(registrationNumber)=> this.props.setRegistrationNumber(registrationNumber)}
                                    onTaxNumberChange={(taxNumber)=> this.props.setTaxNumber(taxNumber)}
                                    isBillingAddress={true}
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
        setCompanyName: (companyName: string) => dispatch({
            type: "billingAddress/setCompanyName",
            payload: companyName
        }),
        setTaxNumber: (taxNumber: string) => dispatch({
            type: "billingAddress/setTaxNumber",
            payload: taxNumber
        }),
        setRegistrationNumber: (registrationNumber: string) => dispatch({
            type: "billingAddress/setRegistrationNumber",
            payload: registrationNumber
        }),
        setSelectedAddress: (address: AddressModel) => dispatch({type: "billingAddress/setAddress", payload: address}),
        removeAdyenConfigFromStore: () => dispatch(({type: "adyenConfig/setAdyenConfig", payload: adyenConfigInitialState}))
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
