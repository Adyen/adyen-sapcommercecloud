class PaymentComponentFactory {

    constructor(checkout, checkoutHelper) {
        this.checkout = checkout;
        this.helper = checkoutHelper;

    }

    paymentConfiguration(label)  {
        return {
            showPayButton: false,
            onChange: (state, component) =>{
                if (!state.isValid) {
                    this.helper.enablePlaceOrder(label);
                }
            },
            onSubmit: (state, component) => {
                if (!state.isValid) {
                    this.helper.enablePlaceOrder(label);
                    return;
                }
                this.helper.makePayment(state.data, component, this.helper.handleResult, label);
            },
            onAdditionalDetails:  (state, component) => {
                this.helper.submitDetails(state.data, this.helper.handleResult);
            },
            onError: (error, component) => {
                console.log('Something went wrong trying to make the Gift Card payment: ' + error);
                this.helper.handleResult(ErrorMessages.PaymentError, true);
            }
        };
    }

    createFromConfigs(paymentMethodConfigs) {
        for (let callback of Object.keys(paymentMethodConfigs)) {
            const params = paymentMethodConfigs[callback];
            if (params && params.hasOwnProperty('label') && !!params.label) {
                for (const label of params.label) {
                    const paramsWithLabel = {
                        ...params,
                        label: label
                    };
                    this[callback](paramsWithLabel);
                }
            } else {
                this[callback](params);
            }
        }
    }

    createCard(params) {
        const {allowedCards, showRememberDetails, cardHolderNameRequired} = params;

        const copyCardBrand= (event) => {
            context.selectedCardBrand = event.brand;
        }

        this.helper.card = new Card(this.checkout, {
            showPayButton: false,
            type: 'card',
            hasHolderName: true,
            holderNameRequired: cardHolderNameRequired,
            enableStoreDetails: showRememberDetails,
            brands: allowedCards,
            onBrand: copyCardBrand
        }).mount("#card-div");

    }

    createOneClickCard(storedCardList) {
        if (storedCardList && storedCardList.length) {
            for (const storedCard of storedCardList) {
                const oneClickCardNode = document.getElementById("one-click-card_" + storedCard.storedPaymentMethodId);
                const oneClickCard = new Card(this.checkout, {
                    showPayButton: false,
                    storedPaymentMethods: storedCard,
                }).mount(oneClickCardNode);
                this.helper.oneClickCards[storedCard.storedPaymentMethodId] = oneClickCard;
            }
        }
    }

    createSepaDirectDebit() {

        const handleOnChange = (event) => {
            var sepaOwnerNameField = document.getElementById('sepaOwnerName');
            var sepaIbanNumberField = document.getElementById('sepaIbanNumber');

            var sepaOwnerName = event.data.paymentMethod["ownerName"]
            var sepaIbanNumber = event.data.paymentMethod["iban"]

            sepaOwnerNameField.value = sepaOwnerName;
            sepaIbanNumberField.value = sepaIbanNumber;
        }

        this.helper.sepaDirectDebit = new Sepa(this.checkout, {
            showPayButton: false,
            onChange: handleOnChange
        }).mount('#adyen_hpp_sepadirectdebit_container')
    }

    createOnlinebankingIN() {
        new OnlineBankingIN(this.checkout, {onChange: this.handleOnChange}).mount('#adyen_hpp_onlinebanking_IN_container');

    }

    createOnlineBankingPL() {
        new OnlineBankingIN(this.checkout, {onChange: this.handleOnChange}).mount('#adyen_hpp_onlineBanking_PL_container');
    }

    createEps(epsDetails) {
        const eps = new EPS(this.checkout, {
            issuers: epsDetails, // The array of issuers coming from the /paymentMethods api call
            onChange: this.handleOnChange // Gets triggered once the shopper selects an issuer
        }).mount('#adyen_hpp_eps_container');
    }

    // TODO: to check
    createUPI() {
        const label = this.getVisibleLabel();
        const uPINode = document.getElementById('adyen-component-button-container-' + label);

        const handlePaymentResult = (result, component) => {
            $.ajax({
                url: ACC.config.encodedContextPath + '/adyen/component/resultHandler',
                type: "POST",
                data: JSON.stringify({
                    resultCode: result.resultCode,
                    sessionData: result.sessionData
                }),
                contentType: "application/json; charset=utf-8",
                success:  (data) => {
                    try {
                        window.location.href = ACC.config.encodedContextPath + "/" + data.replace("redirect:/", "");
                    } catch (e) {
                        console.log('Error redirecting the user to the placeOrder page');
                        this.helper.handleResult(ErrorMessages.PaymentError, true);
                    }
                },
                error: (xmlHttpResponse, exception) => {
                    var responseMessage = xmlHttpResponse.responseJSON;
                    if (xmlHttpResponse.status === 400) {
                        this.helper.handleResult(responseMessage, true);
                    } else {
                        console.log('Error on handling the redirect to the placeOrder page: ' + responseMessage);
                        this.helper.handleResult(ErrorMessages.PaymentError, true);
                    }
                }
            })
        }


        let upi = this.checkout.create('upi', {
            onPaymentCompleted: handlePaymentResult,
            defaultMode: 'vpa',
            showPayButton: true,
        }).mount(uPINode);
    }

    createPaypal(params) {
        const {amount, isImmediateCapture, paypalMerchantId, label} = params;
        const paypal = new PayPal(this.checkout, {
            style: { // Optional configuration for PayPal payment buttons.
                layout: "vertical",
                color: "gold"
            },
            environment: this.checkout.options.environment,
            amount: {
                currency: amount.currency,
                value: amount.value
            },
            configuration: {
                intent: isImmediateCapture ? "capture" : "authorize",
                merchantId: (this.checkout.options.environment === 'test') ? null : paypalMerchantId,  // Your PayPal Merchant ID. Required for accepting live payments.
            },
            blockPayPalCreditButton: true,
            blockPayPalPayLaterButton: true,
            onInit: (data, actions) => {
                actions.enable();
                $(document).on('change', '.adyen-terms-conditions-check',  (event) => {
                    const checked = (event.target.checked)
                    $('.adyen-terms-conditions-check').prop('checked', checked)
                    if (checked) {
                        actions.enable();
                    } else {
                        actions.disable();
                    }
                    this.helper.showCheckTermsAndConditionsError();
                });
            },
            onClick:  ()=> {
                // Show a validation error if the checkbox is not checked
                this.helper.showCheckTermsAndConditionsError();
            },
            onChange:  (state, component)=> {
                if (!state.isValid) {
                    this.helper.enablePlaceOrder(label);
                }
            },
            onSubmit:  (state, component)=> {
                if (!state.isValid) {
                    this.helper.enablePlaceOrder(label);
                    return false;
                }
                this.helper.makePayment(state.data, component, this.helper.handleResult, label);
            },
            onCancel:  (data, component) => {
                // Sets your prefered status of the component when a PayPal payment is cancelled.
                this.helper.handleResult(ErrorMessages.PaymentCancelled, true);
            },
            onError:  (error, component) =>{
                // Sets your prefered status of the component when an error occurs.
                if (error.name === 'CANCEL') {
                    this.helper.handleResult(ErrorMessages.PaymentCancelled, true);
                } else {
                    this.helper.handleResult(ErrorMessages.PaymentError, true);
                }
            },
            onAdditionalDetails:  (state, component) => {
                this.helper.submitDetails(state.data, this.helper.handleResult);
            }
        }).mount('#adyen-component-button-container-' + label);

    }

    createApplePay(params) {
        const {amount, countryCode, applePayMerchantIdentifier, applePayMerchantName, label} = params;
        const applePayConfiguration = {
            //onValidateMerchant is required if you're using your own Apple Pay certificate
            onValidateMerchant: (resolve, reject, validationURL) => {
                if (this.helper.isTermsAccepted(label)) {
                    resolve();
                } else {
                    reject();
                    this.helper.handleResult(ErrorMessages.TermsNotAccepted, true);
                }
                // Your server uses the validation URL to request a session from the Apple Pay server.
                // Call resolve(MERCHANTSESSION) or reject() to complete merchant validation.
                /*validateMerchant(validationURL)
                    .then(response => {
                        // Complete merchant validation with resolve(MERCHANTSESSION) after receiving an opaque merchant session object, MerchantSession
                        resolve(response);
                    })
                    .catch(error => {
                        // Complete merchant validation with reject() if any error occurs
                        reject();
                       });*/
                console.log(validationURL, reject, resolve);
            }
        };
        const adyenComponent = new ApplePay(this.checkout, {
            amount: {
                currency: amount.currency,
                value: amount.value
            },
            countryCode: countryCode,
            configuration: {
                merchantName: applePayMerchantName,
                merchantId: applePayMerchantIdentifier
            },
            // Button config
            buttonType: "plain",
            buttonColor: "black",
            onChange:  (state, component) => {
                if (!state.isValid) {
                    this.helper.enablePlaceOrder(label);
                }
            },
            onSubmit:  (state, component) => {
                if (!state.isValid) {
                    this.helper.enablePlaceOrder(label);
                    return false;
                }
                this.helper.makePayment(state.data, component, this.helper.handleResult, label);
            },
            onClick:  (resolve, reject) => {
                if (this.helper.isTermsAccepted(label)) {
                    resolve();
                } else {
                    reject();
                    this.helper.handleResult(ErrorMessages.TermsNotAccepted, true);
                }
            }
        }).mount('adyen-component-button-container-' + label);

        adyenComponent.isAvailable()
            .then( () => {
                adyenComponent.mount(applePayNode);
            })
            .catch( (e) => {
                // Apple Pay is not available
                console.log('Something went wrong trying to mount the Apple Pay component: ' + e);
                this.helper.handleResult(ErrorMessages.PaymentNotAvailable, true);
            });
    }

    createGooglePay(params) {
        const {amount, merchantAccount, label} = params;
        const googlePayNode = document.getElementById('adyen-component-button-container-' + label);
        const adyenComponent = this.checkout.create("paywithgoogle", {
            environment: this.checkout.options.environment,
            amount: {
                currency: amount.currency,
                value: amount.value
            },
            configuration: {
                gatewayMerchantId: merchantAccount,
                merchantName: merchantAccount
            },
            buttonColor: "white",
            onChange: (state, component) => {
                if (!state.isValid) {
                    this.helper.helper.hideSpinner();
                }
            },
            onSubmit:  (state, component) => {
                if (!state.isValid) {
                    this.helper.helper.hideSpinner();
                    return false;
                }
                this.helper.helper.showSpinner();
                this.helper.helper.makePayment(state.data, component, this.helper.handleResult, label);
            },
            onClick: (resolve, reject) => {
                if (this.helper.helper.isTermsAccepted(label)) {
                    resolve();
                } else {
                    reject();
                    this.helper.helper.handleResult(ErrorMessages.TermsNotAccepted, true);
                }
            }
        });

        adyenComponent.isAvailable()
            .then( () => {
                adyenComponent.mount(googlePayNode);
            })
            .catch( (e) => {
                // Google Pay is not available
                console.log('Something went wrong trying to mount the Google Pay component: ' + e);
                this.helper.handleResult(ErrorMessages.PaymentNotAvailable, true);
            });
    }

    // TODO: to check
    createAmazonPay(params) {
        const {amount, deliveryAddress, amazonPayConfiguration, locale} = params;
        const label = this.getVisibleLabel();
        let componentConfiguration;
        let state = '';
        if (deliveryAddress.country.isocode === 'US') {
            state = deliveryAddress.region.isocodeShort;
        }

        componentConfiguration = {
            environment: this.checkout.options.environment,
            addressDetails: {
                name: deliveryAddress.firstName + ' ' + deliveryAddress.lastName,
                addressLine1: deliveryAddress.line1,
                addressLine2: deliveryAddress.line2,
                city: deliveryAddress.town,
                stateOrRegion: state,
                postalCode: deliveryAddress.postalCode,
                countryCode: deliveryAddress.country.isocode,
                phoneNumber: deliveryAddress.phone || '0'
            },
            region: amazonPayConfiguration.region,
            amount: amount,
            locale: locale,
            configuration: amazonPayConfiguration,
            checkoutMode: 'ProcessOrder',
            productType: 'PayAndShip',
            placement: 'Checkout',
            returnUrl: window.location.origin + ACC.config.encodedContextPath + '/checkout/multi/adyen/summary/amazonpay/placeorder',
            onClick: (resolve, reject) => {
                if (this.isTermsAccepted(label)) {
                    resolve();
                } else {
                    reject();
                    this.handleResult(ErrorMessages.TermsNotAccepted, true);
                }
            }
        };
        const amazonPayNode = document.getElementById('adyen-component-button-container-' + label);
        const adyenComponent = this.checkout.create("amazonpay", componentConfiguration);
        try {
            adyenComponent.mount(amazonPayNode);
        } catch (e) {
            console.log('Something went wrong trying to mount the Amazon Pay component: ' + e);
            this.handleResult(ErrorMessages.PaymentNotAvailable, true);
        }
    }

    createMbway(params) {
        const {label} = params;
        const adyenComponent = new MBWay(this.checkout, this.paymentConfiguration(label)).mount('#adyen-component-container-' + label);
        this.helper.configureButton(adyenComponent, false, label);
    }

    createBlik(params) {
        const {label} = params;
        const adyenComponent = new Blik(this.checkout, this.paymentConfiguration(label)).mount('#adyen-component-container-' + label);
        this.helper.configureButton(adyenComponent, false, label);
    }

    createGiftCard (params) {
        const {label} = params;
        const adyenComponent = new Giftcard(this.checkout, this.paymentConfiguration(label)).mount('#adyen-component-container-' + label);;
        this.helper.configureButton(adyenComponent, false, label);

    }

    createAfterPay(countryCode) {
        this.afterPay = this.checkout.create("afterpay_default", {
            countryCode: countryCode,
            visibility: { // Optional configuration
                personalDetails: "editable",
                billingAddress: "hidden",
                deliveryAddress: "hidden"
            }
        });

        try {
            this.afterPay.mount('#afterpay-container');
        } catch (e) {
            console.log('Something went wrong trying to mount the afterpay component: ' + e);
        }
    }

    createPix(params) {
        const {label, issuers} = params;
        $("#generateqr-" + label).click( () => {
            this.helper.helper.showSpinner();
            if (!this.helper.helper.isTermsAccepted(label)) {
                this.helper.helper.handleResult(ErrorMessages.TermsNotAccepted, true)
            } else {
                $("#generateqr-" + label).hide();
                $(".checkbox").hide();
                var actionHandler = {
                    handleAction: (action) => {
                        this.helper.helper.checkout.createFromAction(action, { //TODO FXIME: check if this is correct
                            issuers: issuers,
                            onAdditionalDetails:  (state) => {
                                this.helper.helper.hideSpinner();
                                this.helper.helper.submitDetails(state.data, this.helper.helper.handleResult);
                            }
                        }).mount('#qrcode-container-' + label);
                        this.helper.helper.hideSpinner();
                    }
                };
                this.helper.helper.makePayment({type: "pix"}, actionHandler, this.helper.helper.handleResult, label);
            }
        });
    }

    createBcmc() {
        this.bcmc = this.checkout.create("bcmc", {
            hasHolderName: true,
            holderNameRequired: true
        });

        try {
            this.bcmc.mount('#bcmc-container');
        } catch (e) {
            console.log('Something went wrong trying to mount the BCMC component: ' + e);
        }
    }

    createBcmcMobile(params) {
        const {label} = params;
        $("#generateqr-" + label).click( () => {
            this.helper.showSpinner();
            if (!this.helper.isTermsAccepted(label)) {
                this.helper.handleResult(ErrorMessages.TermsNotAccepted, true)
            } else {
                $("#generateqr-" + label).hide();
                $(".checkbox").hide();
                var actionHandler = {
                    handleAction:  (action) => {
                        this.helper.checkout.createFromAction(action, { //TODO FXIME: check if this is correct
                            onAdditionalDetails:  (state) => {
                                this.helper.hideSpinner();
                                this.helper.submitDetails(state.data, this.helper.handleResult);
                            }
                        }).mount('#qrcode-container-' + label);
                        this.helper.hideSpinner();
                    }
                };
                this.helper.makePayment({type: "bcmc_mobile"}, actionHandler, this.helper.handleResult, label);
            }
        });
    }

    createBizum(params) {
        const {label} = params;

        $(document).ready( () => {
            $("#placeOrder-" + label).click( () => {
                $(this).prop('disabled', true);

                this.helper.showSpinner();

                let termsCheck = document.getElementById('terms-conditions-check-' + label).checked

                if (termsCheck === false) {
                    this.helper.handleResult(ErrorMessages.TermsNotAccepted, true);
                    return;
                }

                const placeOrderForm = {
                    "securityCode": null,
                    "termsCheck": termsCheck,
                };

                $.ajax({
                    url: ACC.config.encodedContextPath + '/checkout/multi/adyen/summary/placeOrder',
                    type: "POST",
                    data: placeOrderForm,
                    success: (data) => {
                        try {
                            let response = JSON.parse(data);

                            const form = document.createElement('form');
                            form.method = 'POST';
                            form.action = response.url;

                            for (const key in response.data) {
                                form.innerHTML += '<input type="hidden" name="' + key + '" value="' + response.data[key] + '" /> ';
                            }

                            document.body.appendChild(form);
                            form.submit();
                        } catch (e) {
                            console.log('Error place order: ' + e);
                            this.helper.hideSpinner();
                            $("#placeOrder-" + label).prop('disabled', false);
                            this.helper.handleResult(data, true);
                        }
                    },
                    error: (xmlHttpResponse, exception) => {
                        console.log(exception);
                        this.helper.hideSpinner();
                        $("#placeOrder-" + label).prop('disabled', false);
                        this.helper.handleResult(responseMessage, true);
                    }
                });
            });
        });
    }

    createGeneralPayment(params) {
        const {label, paymentType} = params;
        var paymentNode = document.getElementById('adyen-component-container-' + label);
        var adyenComponent = this.checkout.create(paymentType, this.paymentConfiguration);

        try {
            adyenComponent.mount(paymentNode);
            this.configureButton(adyenComponent, false, label);
        } catch (e) {
            console.log('Something went wrong trying to mount component: ' + e);
        }
    }


    // Redirect payment methods
    createIdeal(idealDetails) {
        new Redirect(this.checkout,
            {
                details: idealDetails,
                type: 'ideal',
                showPayButton: false,
                onChange: this.handleOnChange
            })
            .mount("#adyen_hpp_ideal_container");
    }

    initiateWalletIN() {
        new Redirect(this.checkout, {type: 'wallet_IN', onChange: this.handleOnChange
        }).mount('#adyen_hpp_wallet_IN_container');
    }

    initiatePaytm() {
        const paytm = new Redirect(this.checkout, {type: 'paytm', onChange: this.handleOnChange}).mount('#adyen_hpp_paytm_container');
    }

    // Helper methods

    handleOnChange(event) {
        var issuerIdField = document.getElementById('issuerId');
        var issuerId = event.data.paymentMethod.issuer;
        issuerIdField.value = issuerId;
    }

    getVisibleLabel() {
        if (!(window.getComputedStyle(document.getElementById('adyen-checkout-visible-xs')).display === "none")) {
            return 'visible-xs';
        }
        if (!(window.getComputedStyle(document.getElementById('adyen-checkout-hidden-xs')).display === "none")) {
            return 'hidden-xs';
        }
        console.log('Something went wrong while trying to compute current visible label');
        return '';
    }

}