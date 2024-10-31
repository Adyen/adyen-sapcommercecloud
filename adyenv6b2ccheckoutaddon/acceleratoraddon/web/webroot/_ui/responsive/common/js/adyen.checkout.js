// Constants
const CardBrand = {
    Visa: 'visa',
    MasterCard: 'mc',
    Elo: 'elo',
    Maestro: 'maestro',
    Electron: 'electron',
    EloDebit: 'elodebit'
};

const ErrorMessages = {
    PaymentCancelled: 'checkout.error.authorization.payment.cancelled',
    PaymentError: 'checkout.error.authorization.payment.error',
    PaymentNotAvailable: 'checkout.summary.component.notavailable',
    TermsNotAccepted: 'checkout.error.terms.not.accepted'
};

// Helper Functions
function showAlert(message) {
    window.alert(message);
}

function isValidCard(card, isDebitCard, isValidBrandType) {
    return card.isValid && (!isDebitCard || isValidBrandType);
}

class AdyenCheckoutHelper {

    constructor() {
        this.oneClickForms = [];
        this.dobDateFormat = "yy-mm-dd";
        this.securedFieldsData = {};
        this.allowedCards = [];
        this.cseEncryptedForm = null;
        this.checkout = null;
        this.card = null;
        this.oneClickCards = {};
        this.selectedCardBrand = null;
        this.sepaDirectDebit = null;
        this.afterPay = null;
        this.bcmc = null;
        this.paypalButton = null;
        this.formValidator = new AdyenFormValidator(this);
        this.factory = null;
    }
    i =0;

    async initiateCheckout(initConfig, paymentMethodConfigs) {
        const configuration = {
            ...initConfig,
            analytics: {
                enabled: false // Set to false to not send analytics data to Adyen.
            },
            risk: {
                enabled: false
            },
            onPaymentCompleted: (result, component) => {
                console.info(result, component);
            },
            onError: (error, component) => {
                console.error(error.name, error.message, error.stack, component);
            },
        };
        console.log("Execution of initiateCheckout: ", this.i++);
        this.checkout = await AdyenCheckout(configuration);
        this.factory = new PaymentComponentFactory(this.checkout, this);
        this.factory.createFromConfigs(paymentMethodConfigs);
    }

    convertCardBrand() {
        switch (this.selectedCardBrand) {
            case CardBrand.Visa:
                return CardBrand.Electron;
            case CardBrand.MasterCard:
                return CardBrand.Maestro;
            case CardBrand.Elo:
                return CardBrand.EloDebit;
            default:
                return this.selectedCardBrand;
        }
    }

    isValidBrandType() {
        return [CardBrand.Visa, CardBrand.MasterCard, CardBrand.Elo].includes(this.selectedCardBrand);
    }

    getCardType() {
        let cardType = $('#adyen_combo_card_type').val();
        return cardType ? cardType : "credit";
    }

    isDebitCard() {
        return this.getCardType() === 'debit';
    }

    validateForm() {
        return this.formValidator.validateForm();
    }

    copyCardData() {
        const state = this.card.data.paymentMethod;
        $('input[name="encryptedCardNumber"]').val(state.encryptedCardNumber);
        $('input[name="encryptedExpiryMonth"]').val(state.encryptedExpiryMonth);
        $('input[name="encryptedExpiryYear"]').val(state.encryptedExpiryYear);
        $('input[name="encryptedSecurityCode"]').val(state.encryptedSecurityCode);
        $('input[name="cardHolder"]').val(state.holderName);
        if (this.card.data.storePaymentMethod != null) {
            $('input[name="rememberTheseDetails"]').val(this.card.data.storePaymentMethod);
        }

        if (this.isDebitCard()) {
            $('input[name="cardBrand"]').val(this.convertCardBrand());
            $('input[name="cardType"]').val(this.getCardType());
        } else {
            $('input[name="cardBrand"]').val(this.selectedCardBrand);
        }
        $('input[name="browserInfo"]').val(JSON.stringify(this.card.data.browserInfo));
    }

    copyOneClickCardData(recurringReference, cvc, brand) {
        $("#selectedReference").val(recurringReference);
        $('input[name="encryptedSecurityCode"]').val(cvc);
        $('input[name="browserInfo"]').val(JSON.stringify(this.card.data.browserInfo));
        if (brand) {
            $('input[name="cardBrand"]').val(brand);
        }

    }

    copyOneClickCardBrandData(recurringReference, brand) {
        $("#selectedReference").val(recurringReference);
        $('input[name="cardBrand"]').val(brand);
        $('input[name="browserInfo"]').val(JSON.stringify(this.card.data.browserInfo));
    }

    /**
     * Set Custom values for certain payment methods
     */
    setCustomPaymentMethodValues() {
        var paymentMethod = $('input[type=radio][name=paymentMethod]:checked').val();
        var dob = $('#p_method_adyen_hpp_' + paymentMethod + '_dob').val();
        if (dob) {
            $("#dob").val(dob);
        }

        var ssn = $('#p_method_adyen_hpp_' + paymentMethod + '_ssn');
        if (ssn) {
            $("#socialSecurityNumber").val(ssn.val());
        }

        var terminalId = $('#adyen_pos_terminal');
        if (terminalId) {
            $("#terminalId").val(terminalId.val());
        }

        var firstName = $('#p_method_adyen_hpp_' + paymentMethod + '_first_name').val();
        if (firstName) {
            $("#firstName").val(firstName);
        }

        var lastName = $('#p_method_adyen_hpp_' + paymentMethod + '_last_name').val();
        if (lastName) {
            $("#lastName").val(lastName);
        }
    }

    /**
     * Create DatePicker for Date of Birth field
     *
     * @param element
     */
    createDobDatePicker(element) {
        $("." + element).datepicker({
            dateFormat: this.dobDateFormat,
            changeMonth: true,
            changeYear: true,
            yearRange: "-120:+0"
        });
    }


    togglePaymentMethod(paymentMethod) {
        $(".payment_method_details").hide();
        $(".chckt-pm__details").hide();

        $("#dd_method_" + paymentMethod).show();
        $("#adyen_hpp_" + paymentMethod + "_container").show();
    }

    createDfValue() {
        window.dfDo("dfValue");
    }

    checkTermsAndConditions(checked) {
        $('.adyen-terms-conditions-check').prop('checked', checked)
        if (checked) {
            actions.enable();
            $('.adyen-terms-conditions-check-error').addClass('hidden');
        } else {
            actions.disable();
            $('.adyen-terms-conditions-check-error').removeClass('hidden');
        }
    }

    showCheckTermsAndConditionsError(checked) {
        const $errorMessage = $('.adyen-terms-conditions-check-error');
        if ($('.adyen-terms-conditions-check').prop('checked')) {
            $errorMessage.addClass('hidden');
        } else {
            $errorMessage.removeClass('hidden');
        }
    }

    configureButton(form, useSpinner, label) {
        $(document).ready(() => {
            $("#placeOrder-" + label).click(() => {
                $(this).prop('disabled', true);
                if (useSpinner) {
                    this.showSpinner();
                }
                form.submit();
            });
        });
    }

    makePayment(data, component, handleResult, label) {
        $.ajax({
            url: ACC.config.encodedContextPath + '/adyen/component/payment',
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
                    } else if (response.resultCode && (response.resultCode === 'Authorised')) {
                        handleResult(response, false);
                    } else {
                        handleResult(ErrorMessages.PaymentError, true);
                    }
                } catch (e) {
                    console.log('Error parsing makePayment response: ' + response);
                    handleResult(ErrorMessages.PaymentError, true);
                }
            },
            error: function (xmlHttpResponse, exception) {
                var responseMessage = xmlHttpResponse.responseJSON;
                if (xmlHttpResponse.status === 400) {
                    handleResult(responseMessage, true);
                } else {
                    console.log('Error on makePayment: ' + responseMessage);
                    handleResult(ErrorMessages.PaymentError, true);
                }
            }
        })
    }

    submitDetails(data, handleResult) {
        $.ajax({
            url: ACC.config.encodedContextPath + '/adyen/component/submit-details',
            type: "POST",
            data: JSON.stringify(data),
            contentType: "application/json; charset=utf-8",
            success: function (response) {
                try {
                    if (response.resultCode) {
                        handleResult(response, false);
                    } else {
                        handleResult(ErrorMessages.PaymentError, true);
                    }
                } catch (e) {
                    console.log('Error parsing submitDetails response: ' + response);
                    handleResult(ErrorMessages.PaymentError, true);
                }
            },
            error: function (xhr, exception) {
                var responseMessage = xhr.responseJSON;
                if (xhr.status === 400) {
                    handleResult(responseMessage, true);
                } else {
                    console.log('Error on submitDetails: ' + responseMessage);
                    handleResult(ErrorMessages.PaymentError, true);
                }
            }
        })
    }

    isTermsAccepted(label) {
        return document.getElementById('terms-conditions-check-' + label).checked;
    }

    handleResult(data, error) {
        if (error) {
            document.querySelector("#resultCode").value = data.resultCode;
            document.querySelector("#merchantReference").value = data.merchantReference;
            document.querySelector("#isResultError").value = error;
        } else {
            document.querySelector("#resultCode").value = data.resultCode;
            document.querySelector("#merchantReference").value = data.merchantReference;
        }
        document.querySelector("#handleComponentResultForm").submit();
    }

    showSpinner() {
        document.getElementById("spinner_wrapper").style.display = "flex";
    }

    hideSpinner() {
        document.getElementById("spinner_wrapper").style.display = "none";
    }

    enablePlaceOrder(label) {
        //hide spinner
        this.hideSpinner();
        //enable button
        $("#placeOrder-" + label).prop('disabled', false);
    }

    addRiskData() {
        try {
            $('input[name="riskData"]').val(getData());
        } catch (e) {
            //in case of risk data collection script not enabled
        }

    }
}


