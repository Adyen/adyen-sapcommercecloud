import React, { useEffect, useState } from "react";
import { PaymentHeader } from "../headers/PaymentHeader";
import { connect } from "react-redux";
import { ShippingAddressHeading } from "../common/ShippingAddressHeading";
import { AddressModel } from "../../reducers/types";
import { AppState } from "../../reducers/rootReducer";
import { StoreDispatch } from "../../store/store";
import { AddressData } from "../../types/addressData";
import { AddressService } from "../../service/addressService";
import { AdyenConfigService } from "../../service/adyenConfigService";
import { AdyenCheckoutError, Dropin, UIElement } from '@adyen/adyen-web/auto';
import { AdyenConfigData } from "../../types/adyenConfigData";
import { isEmpty, isNotEmpty } from "../../util/stringUtil";
import { routes } from "../../router/routes";
import { Navigate } from "react-router-dom";
import { PaymentError } from "./PaymentError";
import { ScrollHere } from "../common/ScrollTo";
import { adyenConfigInitialState } from "../../reducers/adyenConfigReducer";
import { PaymentDropIn } from "./PaymentDropIn";
import { BillingAddressForm } from "./BillingAddressForm";
import { useAdyenPayment } from "../../hooks/useAdyenPayment";

interface ComponentProps {
    errorCode?: string;
}

interface StoreProps {
    billingAddress: AddressModel;
    shippingAddressFromCart: AddressData;
    adyenConfig: AdyenConfigData;
}

interface DispatchProps {
    setFirstName: (firstName: string) => void;
    setLastName: (lastName: string) => void;
    setCountryCode: (countryCode: string) => void;
    setRegionCode: (countryCode: string) => void;
    setTitleCode: (titleCode: string) => void;
    setLine1: (line1: string) => void;
    setLine2: (line2: string) => void;
    setCity: (city: string) => void;
    setPostCode: (postCode: string) => void;
    setPhoneNumber: (phoneNumber: string) => void;
    setCompanyName: (companyName: string) => void;
    setTaxNumber: (taxNumber: string) => void;
    setRegistrationNumber: (registrationNumber: string) => void;
    setSelectedAddress: (address: AddressModel) => void;
    removeAdyenConfigFromStore: () => void;
}

type Props = StoreProps & DispatchProps & ComponentProps;

const Payment: React.FC<Props> = (props) => {
    const [useDifferentBillingAddress, setUseDifferentBillingAddress] = useState(false);
    const [saveInAddressBook, setSaveInAddressBook] = useState(false);

    const {
        paymentState,
        handlePayment,
        handleAdditionalDetails,
        handleBalanceCheck,
        handleOrderRequest,
        handleError,
        setDropIn,
        setErrorCode
    } = useAdyenPayment(useDifferentBillingAddress, saveInAddressBook, props.billingAddress);

    useEffect(() => {
        AddressService.fetchAddressConfig();
        AdyenConfigService.fetchPaymentMethodsConfig();
    }, []);

    useEffect(() => {
        if (props.errorCode) {
            setErrorCode(props.errorCode);
        }
    }, [props.errorCode, setErrorCode]);

    useEffect(() => {
        return () => {
            props.removeAdyenConfigFromStore();
        };
    }, [props.removeAdyenConfigFromStore]);

    const handleDropInReady = (dropIn: Dropin) => {
        setDropIn(dropIn);
    };

    const handleOrderRequestWithShopperRef = async (resolve: any, reject: any, data: any) => {
        // Add shopperReference from adyenConfig
        const dataWithShopperRef = {
            ...data,
            shopperReference: props.adyenConfig.shopperEmail
        };
        await handleOrderRequest(resolve, reject, dataWithShopperRef);
    };

    const renderErrorMessage = (): React.JSX.Element => {
        if (isNotEmpty(paymentState.errorCode)) {
            return (
                <>
                    <ScrollHere />
                    <PaymentError errorCode={paymentState.errorCode} />
                </>
            );
        }
        return <></>;
    };

    const getThankYouPageURL = (): string => {
        return routes.thankYouPage + "/" + paymentState.orderNumber;
    };

    if (paymentState.redirectToNextStep && isNotEmpty(paymentState.orderNumber)) {
        return <Navigate to={getThankYouPageURL()} />;
    }

    return (
        <>
            <PaymentHeader isActive={true} />
            <div className="step-body">
                <div className="checkout-paymentmethod">
                    <ShippingAddressHeading address={props.shippingAddressFromCart} />
                    
                    <BillingAddressForm
                        useDifferentBillingAddress={useDifferentBillingAddress}
                        saveInAddressBook={saveInAddressBook}
                        billingAddress={props.billingAddress}
                        errorFieldCodes={paymentState.errorFieldCodes}
                        onUseDifferentBillingAddressChange={setUseDifferentBillingAddress}
                        onSaveInAddressBookChange={setSaveInAddressBook}
                        onCountryCodeChange={props.setCountryCode}
                        onRegionCodeChange={props.setRegionCode}
                        onTitleCodeChange={props.setTitleCode}
                        onFirstNameChange={props.setFirstName}
                        onLastNameChange={props.setLastName}
                        onLine1Change={props.setLine1}
                        onLine2Change={props.setLine2}
                        onCityChange={props.setCity}
                        onPostCodeChange={props.setPostCode}
                        onPhoneNumberChange={props.setPhoneNumber}
                        onCompanyNameChange={props.setCompanyName}
                        onRegistrationNumberChange={props.setRegistrationNumber}
                        onTaxNumberChange={props.setTaxNumber}
                        onSelectAddress={props.setSelectedAddress}
                    />

                    {renderErrorMessage()}
                    
                    <PaymentDropIn
                        adyenConfig={props.adyenConfig}
                        shippingAddress={props.shippingAddressFromCart}
                        partialPaymentId={paymentState.partialPaymentId}
                        onPayment={handlePayment}
                        onAdditionalDetails={handleAdditionalDetails}
                        onBalanceCheck={handleBalanceCheck}
                        onOrderRequest={handleOrderRequestWithShopperRef}
                        onError={handleError}
                        onDropInReady={handleDropInReady}
                    />
                </div>
            </div>
        </>
    );
};

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
