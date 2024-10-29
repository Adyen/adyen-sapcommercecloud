var AdyenExpressCheckoutHybris = (function () {
    'use strict';

    var ErrorMessages = {
        PaymentCancelled: 'checkout.error.authorization.payment.cancelled',
        PaymentError: 'checkout.error.authorization.payment.error',
        PaymentNotAvailable: 'checkout.summary.component.notavailable',
        TermsNotAccepted: 'checkout.error.terms.not.accepted'
    };

    return {

        adyenConfig: {
            pageType: null,
            productCode: null
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
                    console.error("Checkout error occured");
                },
            };
            console.log(configuration)
            return await AdyenWeb.AdyenCheckout(configuration);
        },
        initExpressCheckout: async function (params, config) {
            var checkoutPromise = this.initiateCheckout(config);
            checkoutPromise.then((checkout) => {
                this.initiateGooglePayExpress(checkout, params)
                this.initiateApplePayExpress(checkout, p)
            });
        },
        initiateApplePayExpress: async function (checkout, params) {
            const {
                amount,
                countryCode,
                applePayMerchantIdentifier,
                applePayMerchantName,
                pageType,
                productCode
            } = params;

            const applePayNodes = document.getElementsByClassName('adyen-apple-pay-button');

            this.adyenConfig.pageType = pageType;
            this.adyenConfig.productCode = productCode;


            for (let applePayNode of applePayNodes) {
                let applePayComponent = checkout.create("applepay", {
                    amount: {
                        currency: amount.currency,
                        value: amount.value
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
                    onValidateMerchant: (resolve, reject, validationURL) => {
                        resolve();
                    },
                    onClick: function (resolve, reject) {
                        resolve();
                    },
                    onSubmit: function (state, component) {
                        // empty to block session flow, submit logic done in onAuthorized
                    },
                    onAuthorized: (resolve, reject, event) => {
                        var data = this.prepareDataApple(event);
                        this.makePayment(data, this.getAppleUrl(), resolve, reject);
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
                amountDecimal,
                countryCode,
                pageType,
                productCode
            } = params;

            this.adyenConfig.pageType = pageType;
            this.adyenConfig.productCode = productCode;

            const googlePayNodes = document.getElementsByClassName('adyen-google-pay-button');

            const googlePayConfig = {

                // Step 2: Set the callback intents.
                buttonSizeMode: "fill",
                buttonType: "checkout",

                callbackIntents: ['SHIPPING_ADDRESS'],

                // Step 3: Set shipping configurations.

                shippingAddressRequired: true,
                emailRequired: true,

                shippingAddressParameters: {
                    allowedCountryCodes: [],
                    phoneNumberRequired: false
                },

                // Shipping options configurations.
                shippingOptionRequired: false,

                // Step 4: Pass the default shipping options.

                // shippingOptions: {
                //     defaultSelectedOptionId: 'shipping-001',
                //     shippingOptions: [
                //         {
                //             id: 'shipping-001',
                //             label: '$0.00: Free shipping',
                //             description: 'Free shipping: delivered in 10 business days.'
                //         },
                //         {
                //             id: 'shipping-002',
                //             label: '$1.99: Standard shipping',
                //             description: 'Standard shipping: delivered in 3 business days.'
                //         },
                //     ]
                // },

                // Step 5: Set the transaction information.

                //Required for v6.0.0 or later.
                isExpress: true,


                transactionInfo: {
                    countryCode: countryCode,
                    currencyCode: amount.currency,
                    totalPriceStatus: 'FINAL',
                    totalPrice: amountDecimal,
                    totalPriceLabel: 'Total'
                },

                // Step 6: Update the payment data.

                paymentDataCallbacks: {
                    onPaymentDataChanged(intermediatePaymentData) {
                        return new Promise(async resolve => {
                            const {
                                callbackTrigger,
                                shippingAddress,
                                shippingOptionData
                            } = intermediatePaymentData;
                            const paymentDataRequestUpdate = {};

                                // If it initializes or changes the shipping address, calculate the shipping options and transaction info.
                                if (callbackTrigger === 'INITIALIZE' || callbackTrigger === 'SHIPPING_ADDRESS') {
                                    // paymentDataRequestUpdate.newShippingOptionParameters = await fetchNewShippingOptions(shippingAddress.countryCode);
                                    // paymentDataRequestUpdate.newTransactionInfo = calculateNewTransactionInfo(/* ... */);
                                }

                            // If SHIPPING_OPTION changes, calculate the new shipping amount.
                            if (callbackTrigger === 'SHIPPING_OPTION') {
                                // paymentDataRequestUpdate.newTransactionInfo = calculateNewTransactionInfo(/* ... */);
                            }

                            resolve(paymentDataRequestUpdate);
                        });
                    }
                },

                // Step 7: Configure the callback to get the shopper's information.

                onAuthorized: (paymentData) => {
                    this.makePayment(this.prepareDataGoogle(paymentData), this.getGoogleUrl())
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
        makePayment: function(data, url, resolve = ()=>{}, reject = ()=>{}) {
            $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json; charset=utf-8",
                success: function (response) {
                    try {
                        if (response.resultCode && (response.resultCode === 'Authorised' || response.resultCode === 'RedirectShopper')) {
                            resolve();
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
        prepareDataApple: function (event) {
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
        prepareDataGoogle: function(paymentData) {
            let baseData = {
                googlePayDetails: {
                    googlePayToken: paymentData.paymentMethodData.tokenizationData.token,
                    googlePayCardNetwork: paymentData.paymentMethodData.info.cardNetwork
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
                    ...baseData
                }
            }
            if (this.adyenConfig.pageType === 'cart') {
                return baseData;
            }
            console.error('unknown page type')
            return {};
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
        }
    }
})();