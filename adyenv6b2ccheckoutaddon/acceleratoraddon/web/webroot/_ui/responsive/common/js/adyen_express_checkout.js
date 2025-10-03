var AdyenExpressCheckoutHybris = (function () {
    'use strict';

    var ErrorMessages = {
        PaymentCancelled: 'checkout.error.authorization.payment.cancelled',
        PaymentError: 'checkout.error.authorization.payment.error',
        PaymentNotAvailable: 'checkout.summary.component.notavailable',
        TermsNotAccepted: 'checkout.error.terms.not.accepted'
    };

    async function updateDeliveryAddress(cartId, addressData) {
        return new Promise((resolve, reject) => {
            $.ajax({
                url: `${ACC.config.encodedContextPath}/express-checkout/configure/${cartId}/addresses/delivery`,
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(addressData),
                success: resolve,
                error: reject
            });
        });
    }

    async function fetchDeliveryMethods(cartId) {
        return new Promise((resolve, reject) => {
            $.ajax({
                url: `${ACC.config.encodedContextPath}/express-checkout/configure/${cartId}/delivery-methods`,
                type: 'GET',
                contentType: 'application/json',
                success: resolve,
                error: reject
            });
        });
    }

    async function setDeliveryMethod(cartId, deliveryMethodId) {
        return new Promise((resolve, reject) => {
            $.ajax({
                url: `${ACC.config.encodedContextPath}/express-checkout/configure/${cartId}/delivery-method/${deliveryMethodId}`,
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({}),
                success: resolve,
                error: reject
            });
        });
    }

    async function getCartData() {
        const productCode = AdyenExpressCheckoutHybris.adyenConfig.productCode;
        if (AdyenExpressCheckoutHybris.adyenConfig.pageType === 'PDP') {
            return new Promise((resolve, reject) => {
                $.ajax({
                    url: ACC.config.encodedContextPath + `/express-checkout/configure/create-cart/${productCode}`,
                    type: 'POST',
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify({}),
                    success: function (cartData) {
                        $.ajax({
                            url: ACC.config.encodedContextPath + `/express-checkout/configure/${cartData.code}/product/${productCode}/quantity/1`,
                            type: 'POST',
                            contentType: "application/json; charset=utf-8",
                            data: JSON.stringify({}),
                            success: function (response) {
                                resolve(cartData);
                            },
                            error: function (error) {
                                console.error('Error creating cart:', error);
                                reject(error);
                            }
                        });
                    },
                    error: function (error) {
                        console.error('Error creating cart:', error);
                        reject(error);
                    }
                });
            });
        } else {
            return new Promise((resolve, reject) => {
                $.ajax({
                    url: ACC.config.encodedContextPath + '/express-checkout/configure/cart',
                    type: 'GET',
                    contentType: "application/json; charset=utf-8",
                    success: function (cartData) {
                        resolve(cartData);
                    },
                    error: function (error) {
                        console.error('Error creating cart:', error);
                        reject(error);
                    }
                });
            });
        }
    }

    async function handleShippingAddressUpdate(shippingAddress, cartId) {
        const addressData = {
            postalCode: shippingAddress.postalCode,
            country: {isocode: shippingAddress.countryCode}
        };
        await updateDeliveryAddress(cartId, addressData);
        return await fetchDeliveryMethods(cartId);

    }

    return {

        adyenConfig: {
            pageType: null,
            productCode: null,
        },

        initiateCheckout: async function (initConfig) {
            const configuration = {
                ...initConfig,
                analytics: {
                    enabled: false // Set to false to not send analytics data to Adyen.
                },
                risk: {
                    enabled: false
                },
                onError: (error, component) => {
                    console.error("Checkout error occurred");
                },
            };
            return await AdyenWeb.AdyenCheckout(configuration);
        },
        initExpressCheckout: async function (params, config) {
            var checkoutPromise = this.initiateCheckout(config);
            checkoutPromise.then((checkout) => {
                this.adyenConfig.pageType = params.pageType;
                this.adyenConfig.productCode = params.productCode;

                if (params.pageType === 'cart' && params.googlePayExpressEnabledOnCart || params.pageType === 'PDP' && params.googlePayExpressEnabledOnProduct) {
                    this.initiateGooglePayExpress(checkout, params)
                }
                if (params.pageType === 'cart' && params.applePayExpressEnabledOnCart || params.pageType === 'PDP' && params.applePayExpressEnabledOnProduct) {
                    this.initiateApplePayExpress(checkout, params)
                }
                if (params.pageType === 'cart' && params.payPalExpressEnabledOnCart || params.pageType === 'PDP' && params.payPalExpressEnabledOnProduct) {
                    this.initiatePayPalExpress(checkout, params)
                }
            });
        },
        initiateApplePayExpress: async function (checkout, params) {
            const {
                amount,
                applePayMerchantName,
                applePayMerchantId
            } = params;

            const applePayNodes = document.getElementsByClassName('adyen-apple-pay-button');
            let paymentData;
            let cartData;

            for (let applePayNode of applePayNodes) {
                let applePayComponent = new AdyenWeb.ApplePay(checkout, {
                    amount: {
                        currency: amount.currency,
                        value: amount.value
                    },
                    configuration: {
                        merchantName: applePayMerchantName,
                        merchantId: applePayMerchantId,
                    },
                    isExpress: true,
                    // Button config
                    buttonType: "check-out",
                    buttonColor: "black",
                    requiredShippingContactFields: [
                        "postalAddress",
                        "name",
                        "email"
                    ],
                    onShippingContactSelected: async function (resolve, reject, event) {
                        const shippingAddress = {
                            postalCode: event.shippingContact.postalCode,
                            countryCode: event.shippingContact.countryCode
                        }

                        cartData = await getCartData();
                        let deliveryMethods = await handleShippingAddressUpdate(shippingAddress, cartData.code);
                        const validDeliveryMethods = deliveryMethods.filter(mode => mode.code);


                        if (validDeliveryMethods.length > 0) {
                            const defaultDeliveryMethod = validDeliveryMethods[0];

                            const cartDataResponse = await setDeliveryMethod(cartData.code, defaultDeliveryMethod.code);

                            try {
                                const shippingContactUpdate = {
                                    newTotal: {
                                        label: applePayMerchantName,
                                        type: 'final',
                                        amount: cartDataResponse.totalPriceWithTax.value.toString()
                                    },
                                    newShippingMethods: validDeliveryMethods.map(shippingOption => ({
                                        identifier: shippingOption.code,
                                        label: shippingOption.name || "",
                                        detail: shippingOption.description || "",
                                        amount: shippingOption.deliveryCost.value.toString()
                                    }))
                                }
                                resolve(shippingContactUpdate);
                            } catch (e) {
                                console.error("Delivery mode mapping issue")
                                reject();
                            }

                        } else {
                            console.error("No delivery methods available")
                            reject();
                        }
                    },
                    onShippingMethodSelected: async function (resolve, reject, event) {
                        const cartDataResponse = await setDeliveryMethod(cartData.code, event.shippingMethod.identifier);

                        try {
                            const shippingMethodUpdate = {
                                newTotal: {
                                    label: applePayMerchantName,
                                    type: 'final',
                                    amount: cartDataResponse.totalPriceWithTax.value.toString()
                                }
                            }

                            resolve(shippingMethodUpdate)
                        } catch (e) {
                            console.error("Delivery mode selection issue")
                            reject();
                        }
                    },
                    //onValidateMerchant is required if you're using your own Apple Pay certificate
                    onSubmit: (data, component, actions) => {
                        this.makePayment(this.prepareDataApple(paymentData, cartData), this.getAppleUrl(), applePayComponent, actions.resolve, actions.reject)
                    },
                    onAuthorized: (data, actions) => {
                        paymentData = data;
                        actions.resolve()
                    }
                });
                applePayComponent.isAvailable()
                    .then(function () {
                        applePayComponent.mount(applePayNode);
                        applePayNode.parentElement.classList.remove('hidden')
                    })
                    .catch(function (e) {
                        // Apple Pay is not available
                    });
            }
        },
        initiateGooglePayExpress: function (checkout, params) {
            const {
                amount,
                amountDecimal,
                googlePayMerchantId,
                googlePayGatewayMerchantId
            } = params;

            const googlePayNodes = document.getElementsByClassName('adyen-google-pay-button');
            let googlePayComponent;

            let paymentData;
            let cartData;

            const googlePayConfig = {
                configuration: {
                    merchantName: googlePayMerchantId,
                    gatewayMerchantId: googlePayGatewayMerchantId
                },

                buttonSizeMode: "fill",
                buttonType: "checkout",
                buttonRadius: 1,

                callbackIntents: ['SHIPPING_ADDRESS', 'SHIPPING_OPTION'],

                shippingAddressRequired: true,
                emailRequired: true,

                shippingAddressParameters: {
                    allowedCountryCodes: [],
                    phoneNumberRequired: false
                },

                shippingOptionRequired: true,

                isExpress: true,


                transactionInfo: {
                    countryCode: undefined,
                    currencyCode: amount.currency,
                    totalPriceStatus: 'FINAL',
                    totalPrice: amountDecimal,
                    totalPriceLabel: 'Total'
                },

                paymentDataCallbacks: {
                    onPaymentDataChanged(intermediatePaymentData) {
                        return new Promise(async (resolve, reject) => {
                            const {callbackTrigger, shippingAddress, shippingOptionData} = intermediatePaymentData;
                            const paymentDataRequestUpdate = {};

                            try {
                                if (callbackTrigger === 'INITIALIZE') {
                                    cartData = await getCartData();
                                }

                                if (callbackTrigger === 'INITIALIZE' || callbackTrigger === 'SHIPPING_ADDRESS') {
                                    let deliveryMethods = await handleShippingAddressUpdate(shippingAddress, cartData.code);
                                    const validDeliveryMethods = deliveryMethods.filter(mode => mode.code);


                                    if (validDeliveryMethods.length > 0) {
                                        const defaultDeliveryMethod = validDeliveryMethods[0];

                                        const cartDataResponse = await setDeliveryMethod(cartData.code, defaultDeliveryMethod.code);

                                        try {
                                            paymentDataRequestUpdate.newShippingOptionParameters = {
                                                defaultSelectedOptionId: validDeliveryMethods[0].code || "",
                                                shippingOptions: validDeliveryMethods.map(mode => ({
                                                    id: mode.code || "",
                                                    label: mode.name || "",
                                                    description: mode.description || "",
                                                }))
                                            };
                                            paymentDataRequestUpdate.newTransactionInfo = {
                                                countryCode: undefined,
                                                currencyCode: cartDataResponse.totalPriceWithTax.currencyIso,
                                                totalPriceStatus: 'FINAL',
                                                totalPrice: cartDataResponse.totalPriceWithTax.value.toString(),
                                                totalPriceLabel: 'Total'
                                            };
                                        } catch (e) {
                                            console.error("Delivery mode mapping issue")
                                            reject("Delivery mode mapping issue")
                                        }
                                    } else {
                                        console.error("No delivery methods available")
                                        reject("No delivery methods available");
                                    }
                                }

                                if (callbackTrigger === 'SHIPPING_OPTION') {
                                    const cartDataResponse = await setDeliveryMethod(cartData.code, shippingOptionData.id);
                                    try {
                                        paymentDataRequestUpdate.newTransactionInfo = {
                                            countryCode: undefined,
                                            currencyCode: cartDataResponse.totalPriceWithTax.currencyIso,
                                            totalPriceStatus: 'FINAL',
                                            totalPrice: cartDataResponse.totalPriceWithTax?.value.toString(),
                                            totalPriceLabel: 'Total'
                                        };

                                    } catch (e) {
                                        console.error("Delivery mode selection issue")
                                        reject("Delivery mode selection issue")
                                    }
                                }
                                resolve(paymentDataRequestUpdate);
                            } catch (error) {
                                console.error(error);
                                reject(error);
                            }
                        });
                    },
                },

                onSubmit: (state, element, actions) => {
                    this.makePayment(this.prepareDataGoogle(paymentData, cartData), this.getGoogleUrl(), googlePayComponent, actions.resolve, actions.reject)
                },
                onAuthorized: (data, actions) => {
                    paymentData = data;
                    actions.resolve();
                },
                onError: function (error) {
                    console.log(error)
                },

            }

            for (let googlePayNode of googlePayNodes) {
                googlePayComponent = new AdyenWeb.GooglePay(checkout, googlePayConfig);
                googlePayComponent.isAvailable()
                    .then(function () {
                        googlePayComponent.mount(googlePayNode);
                        googlePayNode.parentElement.classList.remove('hidden')
                    })
                    .catch(function (e) {
                        // Google Pay is not available
                        console.log('Something went wrong trying to mount the Google Pay component');
                    });
            }
        },
        initiatePayPalExpress: function (checkout, params) {
            const {
                amount,
                payPalIntent
            } = params;

            const payPalNodes = document.getElementsByClassName('adyen-paypal-button');

            let payPalComponent;
            let cartGuid;
            let pspReference;

            const payPalConfig = {
                amount: {
                    currency: amount.currency,
                    value: amount.value
                },

                isExpress: true,
                blockPayPalVenmoButton: true,
                blockPayPalCreditButton: true,
                blockPayPalPayLaterButton: true,

                intent: payPalIntent,

                style: {
                    borderRadius: 1,
                    height: 48
                },

                onShippingAddressChange: async (data, actions, component) => {

                    const addressData = {
                        postalCode: data.shippingAddress.postalCode,
                        country: {isocode: data.shippingAddress.countryCode}
                    };

                    let response = await this.onPayPalSetShippingAddress({
                        addressData: addressData,
                        cartGuid: cartGuid,
                        pspReference: pspReference,
                        paymentData: component.paymentData
                    });

                    component.updatePaymentData(response.paymentData)

                },
                onShippingOptionsChange: async (data, actions, component) => {
                    let response = await this.onPayPalSetShippingMethod({
                        shippingMethodCode: data.selectedShippingOption.id,
                        cartGuid: cartGuid,
                        pspReference: pspReference,
                        paymentData: component.paymentData
                    });

                    component.updatePaymentData(response.paymentData);
                },
                onSubmit: async (state, component, actions) => {
                    if (this.adyenConfig.pageType === "PDP") {
                        let response = await this.onPayPalPDPSubmit(state.data);
                        cartGuid = response.expressCartGuid;
                        pspReference = response.pspReference;

                        if (response.paymentResponse.action) {
                            component.handleAction(response.paymentResponse.action)
                        }
                    }

                    if (this.adyenConfig.pageType === "cart") {
                        let response = await this.onPayPalCartSubmit(state.data);
                        pspReference = response.pspReference
                        
                        if (response.action) {
                            component.handleAction(response.action)
                        }
                    }
                },
                onAuthorized: (paymentData, actions) => {
                    this.onPayPalAuthorize(this.getPayPalUrl(), this.prepareDataPayPal(paymentData, cartGuid), actions.resolve, actions.reject)
                },
                onAdditionalDetails: (state) => {
                    this.makePayment(state.data, this.getAdditionalDataUrl(), payPalComponent)
                }
            }

            if (payPalNodes.length > 0) {
                payPalComponent = new AdyenWeb.PayPal(checkout, payPalConfig);
                payPalComponent.isAvailable()
                    .then(function () {
                        payPalComponent.mount(payPalNodes[0]);
                        payPalNodes[0].parentElement.classList.remove('hidden')

                    })
                    .catch(function (e) {
                        // PayPal is not available
                        console.log('Something went wrong trying to mount the PayPal component');
                    });

                if (payPalNodes.length > 1) {
                    console.warn("More than one PayPal placeholder")
                }
            }
        },
        onPayPalCartSubmit: function (data) {
            return $.ajax({
                url: ACC.config.encodedContextPath + '/express-checkout/paypal/submit/cart',
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    return response;
                },
                error: function () {
                    throw "PayPal cart submit error"
                }
            })
        },
        onPayPalPDPSubmit: function (data) {
            return $.ajax({
                url: ACC.config.encodedContextPath + '/express-checkout/paypal/submit/PDP',
                type: "POST",
                data: JSON.stringify({
                    payPalDetails: data,
                    productCode: this.adyenConfig.productCode
                }),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    return response;
                },
                error: function () {
                    throw "PayPalPDP submit error"
                }
            })
        },
        onPayPalAuthorize: function (url, data, resolve, reject) {
            $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function () {
                    resolve();
                },
                error: function () {
                    reject();
                }
            })
        },
        onPayPalSetShippingAddress: function (data) {
           return $.ajax({
                url: ACC.config.encodedContextPath + '/express-checkout/paypal/shipping-address',
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    return response;
                },
                error: function () {
                    throw "Set shipping address error"
                }
            })
        },
        onPayPalSetShippingMethod: function (data) {
            return $.ajax({
                url: ACC.config.encodedContextPath + '/express-checkout/paypal/shipping-method',
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    return response;
                },
                error: function () {
                    throw "Set shipping method error"

                }
            })
        },
        makePayment: function (data, url, component, resolve = () => {
        }, reject = () => {
        }) {
            $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    try {
                        if (response.action && (response.resultCode && (response.resultCode === 'Pending' ||
                            response.resultCode === 'RedirectShopper' || response.resultCode === 'IdentifyShopper' ||
                            response.resultCode === 'ChallengeShopper' || response.resultCode === 'PresentToShopper' ||
                            response.resultCode === 'Await') || (response.action && response.action.type))) {
                            component.handleAction(response.action);
                        } else if (response.resultCode && (response.resultCode === 'Authorised' || response.resultCode === 'RedirectShopper')) {
                            resolve({
                                resultCode: response.resultCode
                            });
                            AdyenExpressCheckoutHybris.handleResult(response, false);
                        } else {
                            reject();
                            AdyenExpressCheckoutHybris.handleResult(ErrorMessages.PaymentError, true);
                        }
                    } catch (e) {
                        reject();
                        AdyenExpressCheckoutHybris.handleResult(ErrorMessages.PaymentError, true);
                    }
                },
                error: function (xmlHttpResponse, exception) {
                    reject();
                    var responseMessage = xmlHttpResponse.responseJSON;
                    if (xmlHttpResponse.status === 400) {
                        AdyenExpressCheckoutHybris.handleResult(responseMessage, true);
                    } else {
                        console.log('Error on makePayment');
                        AdyenExpressCheckoutHybris.handleResult(ErrorMessages.PaymentError, true);
                    }
                }
            })
        },
        handleResult: function (data, error) {
            if (error) {
                if (data) {
                    document.querySelector("#resultCode").value = data.resultCode;
                    document.querySelector("#merchantReference").value = data.merchantReference;
                }
                document.querySelector("#isResultError").value = error;
            } else {
                document.querySelector("#resultCode").value = data.resultCode;
                document.querySelector("#merchantReference").value = data.merchantReference;
            }
            document.querySelector("#handleComponentResultForm").submit();
        },
        prepareDataApple: function (paymentData, cartData) {
            const event = paymentData.authorizedEvent;

            return  {
                cartId: cartData.code,
                applePayDetails: {
                    applePayToken: btoa(JSON.stringify(event.payment.token.paymentData))
                },
                addressData: {
                    email: event.payment.shippingContact.emailAddress,
                    firstName: event.payment.shippingContact.givenName,
                    lastName: event.payment.shippingContact.familyName,
                    line1: event.payment.shippingContact.addressLines[0],
                    line2: event.payment.shippingContact.addressLines[1],
                    postalCode: event.payment.shippingContact.postalCode,
                    town: event.payment.shippingContact.locality,
                    country: {
                        isocode: event.payment.shippingContact.countryCode,
                        name: event.payment.shippingContact.country
                    }
                }
            }
        },
        prepareDataGoogle: function (paymentData, cartData) {
            return  {
                cartId: cartData.code,
                googlePayDetails: {
                    googlePayToken: paymentData.authorizedEvent.paymentMethodData.tokenizationData.token,
                    googlePayCardNetwork: paymentData.authorizedEvent.paymentMethodData.info.cardNetwork
                },
                addressData: {
                    email: paymentData.authorizedEvent.email,
                    firstName: paymentData.deliveryAddress.firstName,
                    // lastName: paymentData.payment.shippingContact.familyName,
                    line1: paymentData.deliveryAddress.street,
                    line2: paymentData.deliveryAddress.houseNumberOrName,
                    postalCode: paymentData.deliveryAddress.postalCode,
                    town: paymentData.deliveryAddress.city,
                    country: {
                        isocode: paymentData.deliveryAddress.country,
                    },
                    region: {
                        isocodeShort: paymentData.deliveryAddress.stateOrProvince
                    }
                }
            }
        },
        prepareDataPayPal: function (paymentData, cartGuid) {
            let baseData = {
                payPalDetails: {
                    orderID: paymentData.authorizedEvent.id,
                    payerID: paymentData.authorizedEvent.payer.payer_id
                },
                addressData: {
                    email: paymentData.authorizedEvent.payer.email_address,
                    firstName: paymentData.authorizedEvent.payer.name.given_name,
                    lastName: paymentData.authorizedEvent.payer.name.surname,
                    line1: paymentData.deliveryAddress.street,
                    line2: paymentData.deliveryAddress.houseNumberOrName,
                    postalCode: paymentData.deliveryAddress.postalCode,
                    town: paymentData.deliveryAddress.city,
                    country: {
                        isocode: paymentData.deliveryAddress.country,
                    },
                    region: {
                        isocodeShort: paymentData.deliveryAddress.stateOrProvince
                    }
                }
            }

            if (this.adyenConfig.pageType === 'PDP') {
                return {
                    cartGuid: cartGuid,
                    ...baseData
                }
            }
            if (this.adyenConfig.pageType === 'cart') {
                return baseData;
            }
            console.error('unknown page type')
            return {};
        },
        getAdditionalDataUrl: function () {
            return ACC.config.encodedContextPath + '/adyen/component/submit-details'
        },
        getAppleUrl: function () {
            if (this.adyenConfig.pageType === 'PDP') {
                return ACC.config.encodedContextPath + '/express-checkout/apple/PDP'
            }
            if (this.adyenConfig.pageType === 'cart') {
                return ACC.config.encodedContextPath + '/express-checkout/apple/cart'
            }
            console.error('unknown page type')
            return null;
        },
        getGoogleUrl: function () {
            if (this.adyenConfig.pageType === 'PDP') {
                return ACC.config.encodedContextPath + '/express-checkout/google/PDP'
            }
            if (this.adyenConfig.pageType === 'cart') {
                return ACC.config.encodedContextPath + '/express-checkout/google/cart'
            }
            console.error('unknown page type')
            return null;
        },
        getPayPalUrl: function () {
            if (this.adyenConfig.pageType === 'PDP') {
                return ACC.config.encodedContextPath + '/express-checkout/paypal/PDP'
            }
            if (this.adyenConfig.pageType === 'cart') {
                return ACC.config.encodedContextPath + '/express-checkout/paypal/cart'
            }
            console.error('unknown page type')
            return null;
        }
    }
})();