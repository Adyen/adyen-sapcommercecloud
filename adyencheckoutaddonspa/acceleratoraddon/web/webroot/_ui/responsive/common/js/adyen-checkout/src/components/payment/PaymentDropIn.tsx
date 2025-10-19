import React, { RefObject, useEffect, useRef } from "react";
import { AdyenCheckout, AdyenCheckoutError, Dropin, ICore } from '@adyen/adyen-web/auto';
import '@adyen/adyen-web/styles/adyen.css';
import {
    AdditionalDetailsActions,
    CardConfiguration,
    CoreConfiguration,
    SubmitActions,
    UIElement
} from "@adyen/adyen-web";
import { AdyenConfigData } from "../../types/adyenConfigData";
import { AddressData } from "../../types/addressData";

interface PaymentDropInProps {
    adyenConfig: AdyenConfigData;
    shippingAddress: AddressData;
    partialPaymentId?: string;
    onPayment: (state: any, element: UIElement, actions: SubmitActions) => void;
    onAdditionalDetails: (state: any, element: UIElement, actions: AdditionalDetailsActions) => void;
    onBalanceCheck: (resolve: any, reject: any, data: any) => Promise<void>;
    onOrderRequest: (resolve: any, reject: any, data: any) => Promise<void>;
    onError: (error: AdyenCheckoutError, element?: UIElement) => void;
    onDropInReady: (dropIn: Dropin) => void;
}

export const PaymentDropIn: React.FC<PaymentDropInProps> = ({
    adyenConfig,
    shippingAddress,
    partialPaymentId,
    onPayment,
    onAdditionalDetails,
    onBalanceCheck,
    onOrderRequest,
    onError,
    onDropInReady
}) => {
    const paymentRef: RefObject<HTMLDivElement> = useRef(null);
    const dropInRef = useRef<Dropin | null>(null);

    const castToEnvironment = (env: string): CoreConfiguration['environment'] => {
        const validEnvironments: CoreConfiguration['environment'][] = ['test', 'live', 'live-us', 'live-au', 'live-apse', 'live-in'];
        if (validEnvironments.includes(env as CoreConfiguration['environment'])) {
            return env as CoreConfiguration['environment'];
        }
        throw new Error(`Invalid environment: ${env}`);
    };

    const getAdyenCheckoutConfig = (): CoreConfiguration => {
        return {
            paymentMethodsResponse: {
                paymentMethods: adyenConfig.paymentMethods,
                storedPaymentMethods: adyenConfig.storedPaymentMethodList
            },
            locale: adyenConfig.shopperLocale,
            environment: castToEnvironment(adyenConfig.environmentMode),
            clientKey: adyenConfig.adyenClientKey,
            countryCode: adyenConfig.countryCode,
            amount: adyenConfig.amount,
            analytics: {
                enabled: false
            },
            // @ts-ignore
            risk: {
                enabled: true
            },
            onError: onError,
            onSubmit: (state: any, element: UIElement, actions: SubmitActions) => onPayment(state.data, element, actions),
            onAdditionalDetails: (state: any, element: UIElement, actions: AdditionalDetailsActions) => onAdditionalDetails(state, element, actions),
            onBalanceCheck: async (resolve: any, reject: any, data: any) => await onBalanceCheck(resolve, reject, {...data, amount: adyenConfig.amount}),
            onOrderRequest: async (resolve: any, reject: any, data: any) => await onOrderRequest(resolve, reject, {...data, amount: adyenConfig.amount})
        };
    };

    const getAdyenCardConfig = (): CardConfiguration => {
        return {
            type: 'card',
            hasHolderName: true,
            holderNameRequired: adyenConfig.cardHolderNameRequired,
            enableStoreDetails: adyenConfig.showRememberTheseDetails,
            clickToPayConfiguration: {
                merchantDisplayName: adyenConfig.merchantDisplayName,
                shopperEmail: adyenConfig.shopperEmail,
                locale: adyenConfig.clickToPayLocale,
            }
        };
    };

    const initializeDropIn = async () => {
        if (!adyenConfig.adyenClientKey || !paymentRef.current) {
            return;
        }

        try {
            const adyenCheckout = await AdyenCheckout(getAdyenCheckoutConfig());
            
            const dropIn = new Dropin(adyenCheckout, {
                paymentMethodsConfiguration: {
                    card: getAdyenCardConfig(),
                    boletobancario: {
                        // @ts-ignore
                        personalDetailsRequired: true,
                        billingAddressRequired: false,
                        showEmailAddress: false,
                        data: {
                            firstName: shippingAddress.firstName,
                            lastName: shippingAddress.lastName,
                        }
                    },
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
                showPayButton: true,
                showRemovePaymentMethodButton: true,
                isPartialPayment: true,
                showRemainingAmount: true
            }).mount(paymentRef.current);

            dropInRef.current = dropIn;
            onDropInReady(dropIn);
        } catch (error) {
            console.error('Failed to initialize Adyen DropIn:', error);
            onError(error as AdyenCheckoutError);
        }
    };

    useEffect(() => {
        initializeDropIn();
        
        return () => {
            if (dropInRef.current) {
                dropInRef.current.unmount();
            }
        };
    }, [adyenConfig.adyenClientKey]);

    return <div className="dropin-payment" ref={paymentRef} />;
};