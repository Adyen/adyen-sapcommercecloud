import { useState, useCallback, useRef } from 'react';
import { Dropin } from '@adyen/adyen-web/auto';
import { PaymentService, PlaceOrderResponse } from '../service/paymentService';
import { AddressModel } from '../reducers/types';
import { AddressData } from '../types/addressData';
import { SubmitActions, AdditionalDetailsActions } from '@adyen/adyen-web';

interface PaymentState {
    errorCode: string;
    errorFieldCodes: string[];
    orderNumber: string;
    partialPaymentId?: string;
    redirectToNextStep: boolean;
}

interface UseAdyenPaymentReturn {
    paymentState: PaymentState;
    dropIn: Dropin | null;
    handlePayment: (data: any, element: any, actions: SubmitActions) => Promise<void>;
    handleAdditionalDetails: (data: any, element: any, actions: AdditionalDetailsActions) => Promise<void>;
    handleBalanceCheck: (resolve: any, reject: any, data: any) => Promise<void>;
    handleOrderRequest: (resolve: any, reject: any, data: any) => Promise<void>;
    handleError: () => Promise<void>;
    resetDropInComponent: () => void;
    setDropIn: (dropIn: Dropin) => void;
    setErrorCode: (errorCode: string) => void;
    setPartialPaymentId: (id: string) => void;
}

export const useAdyenPayment = (
    useDifferentBillingAddress: boolean,
    saveInAddressBook: boolean,
    billingAddress: AddressModel,
    shippingAddress: AddressData
): UseAdyenPaymentReturn => {
    const [paymentState, setPaymentState] = useState<PaymentState>({
        errorCode: '',
        errorFieldCodes: [],
        orderNumber: '',
        partialPaymentId: undefined,
        redirectToNextStep: false
    });

    const [dropIn, setDropInState] = useState<Dropin | null>(null);
    const partialPaymentIdRef = useRef<string | undefined>(paymentState.partialPaymentId);

    const setDropIn = useCallback((newDropIn: Dropin) => {
        setDropInState(newDropIn);
    }, []);

    const setErrorCode = useCallback((errorCode: string) => {
        setPaymentState(prev => ({ ...prev, errorCode }));
    }, []);

    const setPartialPaymentId = useCallback((partialPaymentId: string) => {
        partialPaymentIdRef.current = partialPaymentId;
        setPaymentState(prev => ({ ...prev, partialPaymentId }));
    }, []);

    const isSaveInAddressBook = useCallback((): boolean => {
        return saveInAddressBook && useDifferentBillingAddress;
    }, [saveInAddressBook, useDifferentBillingAddress]);

    const resetDropInComponent = useCallback(() => {
        if (dropIn) {
            dropIn.unmount();
            const element = document.querySelector('.dropin-payment');
            if (element) {
                dropIn.mount(element as HTMLElement);
            }
        }
    }, [dropIn]);

    const handleResponse = useCallback(async (
        response: Promise<void | PlaceOrderResponse>,
        actions: SubmitActions
    ) => {
        setPaymentState(prev => ({ ...prev, errorFieldCodes: [] }));

        const responseData = await response;
        if (!!responseData) {
            if (responseData.success) {
                if (responseData.executeAction) {
                    if (responseData.paymentsAction) {
                        dropIn?.handleAction(responseData.paymentsAction);
                    }
                } else {
                    // Handle partial payment scenarios
                    if (responseData.paymentsResponse &&
                        responseData.paymentsResponse.order &&
                        responseData.paymentsResponse.order.remainingAmount &&
                        responseData.paymentsResponse.order.remainingAmount.value > 0) {

                        // Inform DropIn about partial payment success
                        actions.resolve(responseData.paymentsResponse);
                        
                    } else {
                        // Handle complete payment (no remaining amount)
                        actions.resolve({
                            resultCode: responseData.paymentsResponse?.resultCode || 'Authorised'
                        });
                        
                        // Only redirect if payment is fully completed
                        if (responseData.orderNumber) {
                            setPaymentState(prev => ({
                                ...prev,
                                orderNumber: responseData.orderNumber || '',
                                redirectToNextStep: true
                            }));
                        }
                    }
                }
            } else {
                setPaymentState(prev => ({
                    ...prev,
                    errorFieldCodes: responseData.errorFieldCodes || []
                }));
                // Call resetDropInComponent directly without dependency
                if (dropIn) {
                    dropIn.unmount();
                    const element = document.querySelector('.dropin-payment');
                    if (element) {
                        dropIn.mount(element as HTMLElement);
                    }
                }
            }
            setPaymentState(prev => ({ ...prev, errorCode: responseData.error || '' }));
        }
    }, [dropIn]);

    const handlePayment = useCallback(async (data: any, element: any, actions: SubmitActions) => {
        // Extract billing address from Adyen form
        const billingAddressFromCard = data.billingAddress;
        
        let finalBillingAddress = billingAddress;
        let usesDifferentBillingAddress = useDifferentBillingAddress;
        
        const fallbackFirstName = billingAddress?.firstName || shippingAddress?.firstName || '';
        const fallbackLastName = billingAddress?.lastName || shippingAddress?.lastName || '';

        // Only use different billing address if user explicitly chose it
        if (useDifferentBillingAddress) {
            // If user chose different billing address, check if we have one filled
            if (billingAddress?.firstName && billingAddress?.lastName && billingAddress?.line1) {
                // Use user-filled address from form
                finalBillingAddress = billingAddress;
            } else if (billingAddressFromCard && (billingAddressFromCard.street || billingAddressFromCard.city)) {
                // Fallback to Adyen billing address if form is empty but Adyen collected address
                finalBillingAddress = {
                    id: billingAddress?.id || '',
                    firstName: fallbackFirstName,
                    lastName: fallbackLastName,
                    line1: billingAddressFromCard.street || '',
                    line2: billingAddressFromCard.houseNumberOrName || '',
                    city: billingAddressFromCard.city || '',
                    postalCode: billingAddressFromCard.postalCode || '',
                    countryCode: billingAddressFromCard.country || '',
                    country: billingAddress?.country || '',
                    regionCode: billingAddressFromCard.stateOrProvince !== 'N/A' ? billingAddressFromCard.stateOrProvince : (billingAddress?.regionCode || ''),
                    region: billingAddress?.region || '',
                    phoneNumber: billingAddress?.phoneNumber || '',
                    titleCode: billingAddress?.titleCode || '',
                    title: billingAddress?.title || '',
                    companyName: billingAddress?.companyName || '',
                    taxNumber: billingAddress?.taxNumber || '',
                    registrationNumber: billingAddress?.registrationNumber || ''
                };
            }
        } else {
            // User wants to use shipping address - send null
            finalBillingAddress = null;
        }

        const adyenPaymentForm = PaymentService.preparePlaceOrderRequest(
            data,
            usesDifferentBillingAddress,
            isSaveInAddressBook(),
            finalBillingAddress,
            partialPaymentIdRef.current
        );
        
        await handleResponse(PaymentService.placeOrder(adyenPaymentForm), actions);
    }, [useDifferentBillingAddress, billingAddress, shippingAddress, handleResponse, isSaveInAddressBook]);

    const handleAdditionalDetails = useCallback(async (data: any, element: any, actions: AdditionalDetailsActions) => {
        await handleResponse(PaymentService.sendAdditionalDetails(data), actions);
    }, [handleResponse]);

    const handleBalanceCheck = useCallback(async (resolve: any, reject: any, data: any) => {
        try {
           
            const paymentMethod = data.paymentMethod || {};
            const cardNumber = paymentMethod.number || paymentMethod.encryptedCardNumber || paymentMethod.cardNumber;
            const pin = paymentMethod.cvc || paymentMethod.encryptedSecurityCode || paymentMethod.pin;
            const amount = data.amount;
            
            if (!cardNumber) {
                reject();
                return;
            }
            
            if (!amount) {
                reject();
                return;
            }
            
            const response = await PaymentService.checkGiftCardBalance({
                cardNumber: cardNumber,
                pin: pin,
                amount: amount,
                brand: paymentMethod.brand,
                type: paymentMethod.type
            });
            
            if (response.partialPaymentId) {
                partialPaymentIdRef.current = response.partialPaymentId;
                setPaymentState(prev => ({
                    ...prev,
                    partialPaymentId: response.partialPaymentId
                }));
            }

            const balanceResponse = {
                balance: response.balance,
                transactionLimit: response.transactionLimit || response.balance,
                partialPaymentId: response.partialPaymentId,
                chargedAmount: response.chargedAmount,
                remainingAmount: response.remainingAmount
            };
            resolve(balanceResponse);
        } catch (error) {
            reject();
        }
    }, []);

    const handleOrderRequest = useCallback(async (resolve: any, reject: any, data: any) => {
        try {
           
            const response = await PaymentService.createPartialPaymentOrder({
                amount: data.amount,
                paymentMethod: data.paymentMethod,
                shopperReference: undefined,
                partialPaymentId: partialPaymentIdRef.current
            });
            
            if (!response.orderData || !response.pspReference) {
                reject();
                return;
            }

            const orderResponse = {
                orderData: response.orderData,
                pspReference: response.pspReference
            };
            resolve(orderResponse);
            
        } catch (error) {
            reject();
        }
    }, []);

    const handleError = useCallback(async () => {
        await PaymentService.sendPaymentCancel();
        // Call resetDropInComponent logic directly without dependency
        if (dropIn) {
            dropIn.unmount();
            const element = document.querySelector('.dropin-payment');
            if (element) {
                dropIn.mount(element as HTMLElement);
            }
        }
    }, [dropIn]);

    return {
        paymentState,
        dropIn,
        handlePayment,
        handleAdditionalDetails,
        handleBalanceCheck,
        handleOrderRequest,
        handleError,
        resetDropInComponent,
        setDropIn,
        setErrorCode,
        setPartialPaymentId
    };
};