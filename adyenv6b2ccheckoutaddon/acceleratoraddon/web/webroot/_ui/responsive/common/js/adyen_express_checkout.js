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

    return {

        adyenConfig: {
            pageType: null,
            productCode: null,
            expressCartGuid: null
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
                pageType,
                productCode,
                applePayMerchantName,
                applePayMerchantId
            } = params;

            const applePayNodes = document.getElementsByClassName('adyen-apple-pay-button');

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
                    // Button config
                    buttonType: "check-out",
                    buttonColor: "black",
                    requiredShippingContactFields: [
                        "postalAddress",
                        "name",
                        "email"
                    ],
                    //might be used to recalculate cart with shipping method
//                  onShippingContactSelected: function(resolve, reject, event){
//
//                    var shippingMethodUpdate = {
//                        newTotal: {
//                            amount: amount.value
//                        }
//                    }
//                    resolve(shippingMethodUpdate);
//                },
                    //onValidateMerchant is required if you're using your own Apple Pay certificate
                    onSubmit: function (state, component) {
                        // empty to block session flow, submit logic done in onAuthorized
                    },
                    onAuthorized: (paymentData, actions) => {
                        this.makePayment(this.prepareDataApple(paymentData), this.getAppleUrl(), actions.resolve, actions.reject);
                    }
                });
                applePayComponent.isAvailable()
                    .then(function () {
                        applePayComponent.mount(applePayNode);
                    })
                    .catch(function (e) {
                        // Apple Pay is not available
                    });
            }
        },
        initiateGooglePayExpress: function (checkout, params) {
            const {
                amount,
                pageType,
                productCode,
                amountDecimal,
                countryCode,
            } = params;

            const googlePayNodes = document.getElementsByClassName('adyen-google-pay-button');

            let paymentData;
            let cartData
            if(pageType === 'PDP') {
                cartData = $.ajax({
                    url: ACC.config.encodedContextPath + '/express-checkout/configure/create-cart',
                    type: 'POST',
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify({}),
                    success: function (cartData) {
                        $.ajax({
                            url: ACC.config.encodedContextPath + `/express-checkout/configure/${cartData.code}/product/${productCode}/quantity/1`,
                            type: 'POST',
                            contentType: "application/json; charset=utf-8",
                            data: JSON.stringify({}),
                            success: function (cartData) {
                                // Handle the response data
                                console.log('Cart created:', cartData);
                                return cartData;
                            },
                            error: function (error) {
                                // Handle any errors
                                console.error('Error creating cart:', error);
                            }
                        });
                        console.log('Cart created:', cartData);
                        return cartData;
                    },
                    error: function (error) {
                        // Handle any errors
                        console.error('Error creating cart:', error);
                    }
                });
            }else{
                cartData = $.ajax({
                    url: ACC.config.encodedContextPath + '/express-checkout/configure/cart',
                    type: 'GET',
                    contentType: "application/json; charset=utf-8",
                    success: function (cartData) {
                        return cartData;
                    },
                    error: function (error) {
                        // Handle any errors
                        console.error('Error creating cart:', error);
                    }
                });
            }


            const googlePayConfig = {

                buttonSizeMode: "fill",
                buttonType: "checkout",

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
                    countryCode: countryCode,
                    currencyCode: amount.currency,
                    totalPriceStatus: 'FINAL',
                    totalPrice: amountDecimal,
                    totalPriceLabel: 'Total'
                },

                paymentDataCallbacks: {
                    onPaymentDataChanged(intermediatePaymentData) {
                        return new Promise(async resolve => {
                            const { callbackTrigger, shippingAddress, shippingOptionData } = intermediatePaymentData;
                            const paymentDataRequestUpdate = {};

                            if (callbackTrigger === 'INITIALIZE' || callbackTrigger === 'SHIPPING_ADDRESS') {
                                try {
                                    const addressData = {
                                        postalCode: shippingAddress.postalCode,
                                        country: { isocode: shippingAddress.countryCode }
                                    };
                                    await updateDeliveryAddress(cartData.responseJSON.code, addressData);
                                    const deliveryMethodsResponse = await fetchDeliveryMethods(cartData.responseJSON.code);
                                    const cartDataResponse = await setDeliveryMethod(cartData.responseJSON.code, deliveryMethodsResponse[0]?.code || "");

                                    paymentDataRequestUpdate.newShippingOptionParameters = {
                                        defaultSelectedOptionId: deliveryMethodsResponse[0]?.code || "",
                                        shippingOptions: deliveryMethodsResponse.map(mode => ({
                                            id: mode.code || "",
                                            label: mode.name || "",
                                            description: mode.description || ""
                                        }))
                                    };
                                    paymentDataRequestUpdate.newTransactionInfo = {
                                        countryCode: countryCode,
                                        currencyCode: cartDataResponse.totalPriceWithTax?.currencyIso ?? '',
                                        totalPriceStatus: 'FINAL',
                                        totalPrice: (cartDataResponse.totalPriceWithTax?.value ?? 0).toString(),
                                        totalPriceLabel: 'Total'
                                    };

                                    resolve(paymentDataRequestUpdate);
                                } catch (error) {
                                    console.error(error);
                                }
                            }

                            if (callbackTrigger === 'SHIPPING_OPTION') {
                                try {
                                    const cartDataResponse = await setDeliveryMethod(cartData.responseJSON.code, shippingOptionData.id);

                                    paymentDataRequestUpdate.newTransactionInfo = {
                                        countryCode: countryCode,
                                        currencyCode: cartDataResponse.totalPriceWithTax?.currencyIso ?? '',
                                        totalPriceStatus: 'FINAL',
                                        totalPrice: (cartDataResponse.totalPriceWithTax?.value ?? 0).toString(),
                                        totalPriceLabel: 'Total'
                                    };

                                    resolve(paymentDataRequestUpdate);
                                } catch (error) {
                                    console.error('Error fetching delivery modes:', error);
                                }
                            }
                        });
                    }
                },

                onSubmit: (state, element, actions) => {
                    this.makePayment(this.prepareDataGoogle(paymentData,cartData), this.getGoogleUrl(), actions.resolve, actions.reject)
                },
                onAuthorized: (data, actions) => {
                    paymentData = data;
                    actions.resolve();
                },
                onError: function (error) {
                    console.log(error)
                }
            }

            for (let googlePayNode of googlePayNodes) {
                let googlePayComponent = new AdyenWeb.GooglePay(checkout, googlePayConfig);
                googlePayComponent.isAvailable()
                    .then(function () {
                        googlePayComponent.mount(googlePayNode);
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

                onSubmit: (state, component, actions) => {
                    if (this.adyenConfig.pageType === "PDP") {
                        this.onPayPalPDPSubmit(state.data, actions.resolve, actions.reject, component);
                    }

                    if (this.adyenConfig.pageType === "cart") {
                        this.onPayPalCartSubmit(state.data, actions.resolve, actions.reject, component)
                    }
                },
                onAuthorized: (paymentData, actions) => {
                    this.onPayPalAuthorize(this.getPayPalUrl(), this.prepareDataPayPal(paymentData), actions.resolve, actions.reject)
                },
                onAdditionalDetails: (state) => {
                    this.makePayment(state.data, this.getAdditionalDataUrl())
                }
            }

            if (payPalNodes.length > 0) {
                payPalComponent = new AdyenWeb.PayPal(checkout, payPalConfig);
                payPalComponent.isAvailable()
                    .then(function () {
                        payPalComponent.mount(payPalNodes[0]);
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
        onPayPalCartSubmit: function (data, resolve, reject, component) {
            $.ajax({
                url: ACC.config.encodedContextPath + '/express-checkout/paypal/submit/cart',
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    console.log(response)
                    if (response.action) {
                        component.handleAction(response.action)
                    }
                },
                error: function () {
                    reject();
                }
            })
        },
        onPayPalPDPSubmit: function (data, resolve, reject, component) {
            $.ajax({
                url: ACC.config.encodedContextPath + '/express-checkout/paypal/submit/PDP',
                type: "POST",
                data: JSON.stringify({
                    payPalDetails: data,
                    productCode: this.adyenConfig.productCode
                }),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    console.log(response)
                    AdyenExpressCheckoutHybris.adyenConfig.expressCartGuid = response.expressCartGuid;
                    if (response.paymentResponse.action) {
                        component.handleAction(response.paymentResponse.action)
                    }
                },
                error: function () {
                    reject();
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
        makePayment: function (data, url, resolve = () => {
        }, reject = () => {
        }) {
            $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    try {
                        if (response.resultCode && (response.resultCode === 'Authorised' || response.resultCode === 'RedirectShopper')) {
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
        prepareDataApple: function (paymentData) {
            const event = paymentData.authorizedEvent;

            const baseData = {
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

            if (this.adyenConfig.pageType === 'PDP') {
                return {
                    productCode: this.adyenConfig.productCode,
                    ...baseData
                }
            }
            if (this.adyenConfig.pageType === 'cart') {
                return baseData
            }
            console.error('unknown page type')
            return {};
        },
        prepareDataGoogle: function (paymentData, cartData) {
            let baseData = {
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

            if (this.adyenConfig.pageType === 'PDP') {
                return {
                    productCode: this.adyenConfig.productCode,
                    cartId : cartData.responseJSON.code,
                    ...baseData
                }
            }
            if (this.adyenConfig.pageType === 'cart') {
                return baseData;
            }
            console.error('unknown page type')
            return {};
        },
        prepareDataPayPal: function (paymentData) {
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
                    cartGuid: this.adyenConfig.expressCartGuid,
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