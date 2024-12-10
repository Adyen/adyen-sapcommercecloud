class AdyenFormValidator {
    constructor(adyenCheckoutHybris) {
        this.adyenCheckoutHybris = adyenCheckoutHybris;
    }

    showAlert(message) {
        window.alert(message);
    }

    isValidCard(card, isDebitCard, isValidBrandType) {
        return card.isValid && (!isDebitCard || isValidBrandType);
    }

    validateForm() {
        const paymentMethod = $('input[type=radio][name=paymentMethod]:checked').val();

        if (!paymentMethod) {
            this.showAlert("Please select a payment method");
            return false;
        }

        if (paymentMethod === "adyen_cc") {
            if (!this.isValidCard(this.adyenCheckoutHybris.card, this.adyenCheckoutHybris.isDebitCard(), this.adyenCheckoutHybris.isValidBrandType())) {
                this.showAlert('Please check your card details.');
                this.adyenCheckoutHybris.card.showValidation();
                document.getElementById("card-div").scrollIntoView();
                return false;
            }
            this.adyenCheckoutHybris.copyCardData();
        }

        if (paymentMethod.startsWith("adyen_oneclick_")) {
            const recurringReference = paymentMethod.slice("adyen_oneclick_".length);
            const oneClickCard = this.adyenCheckoutHybris.oneClickCards[recurringReference];

            if (!(oneClickCard && oneClickCard.isValid)) {
                this.showAlert('This credit card is not allowed');
                return false;
            }
            if (['bcmc', 'maestro'].includes(oneClickCard.props.brand)) {
                this.adyenCheckoutHybris.copyOneClickCardBrandData(recurringReference, oneClickCard.props.brand);
            } else {
                this.adyenCheckoutHybris.copyOneClickCardData(recurringReference, oneClickCard.data.paymentMethod.encryptedSecurityCode, oneClickCard.props.brand);
            }
        }
        $('input[name="txvariant"]').remove();

        if (['eps', 'ideal', 'onlinebanking_IN', 'onlineBanking_PL'].indexOf(paymentMethod) >= 0) {
            var issuerIdField = document.getElementById('issuerId');
            if (issuerIdField.value === "") {
                window.alert("Please select an issuer");
                return false;
            }
        }

        if (paymentMethod === "pos") {
            var terminalId = $('#adyen_pos_terminal');
            if (terminalId.val() === "") {
                window.alert("Please select a terminal");
                return false;
            }
        }

        if (paymentMethod === "sepadirectdebit") {
            if (!this.adyenCheckoutHybris.sepaDirectDebit.state.isValid) {
                window.alert("Invalid SEPA Owner Name and IBAN number");
                return false;
            }
        }

        if (paymentMethod === "afterpay_default") {
            if (!this.adyenCheckoutHybris.afterPay.state.isValid) {
                window.alert("Please fill all the details");
                this.adyenCheckoutHybris.afterPay.showValidation();
                document.getElementById("afterpay-container").scrollIntoView();
                return false;
            }
            var dob = $("input[name=dateOfBirth]").val();
            if (dob) {
                $("#dob").val(dob);
            }
        }

        if (paymentMethod === "paybright") {
            var phoneNumber = $("#p_method_adyen_hpp_paybright_telephonenumber").val();
            if (!phoneNumber) {
                window.alert("Please fill phone number");
                document.getElementById("p_method_adyen_hpp_paybright_telephonenumber").scrollIntoView();
                return false;
            }
        }
        if (paymentMethod === "giftcard") {
            $('input[name="giftCardBrand"]').val($('input[type=radio][name=paymentMethod]:checked').attr('brand'));
        }

        if (paymentMethod === "bcmc") {
            if (!this.adyenCheckoutHybris.bcmc.state.isValid) {
                window.alert("Please fill all the details");
                this.adyenCheckoutHybris.bcmc.showValidation();
                document.getElementById("bcmc-container").scrollIntoView();
                return false;
            }
            var state = this.adyenCheckoutHybris.bcmc.data.paymentMethod;
            $('input[name="encryptedCardNumber"]').val(state.encryptedCardNumber);
            $('input[name="encryptedExpiryMonth"]').val(state.encryptedExpiryMonth);
            $('input[name="encryptedExpiryYear"]').val(state.encryptedExpiryYear);
            $('input[name="cardHolder"]').val(state.holderName);
            $('input[name="cardBrand"]').val('bcmc');
            $('input[name="cardType"]').val('debit');
            $('input[name="browserInfo"]').val(JSON.stringify(this.adyenCheckoutHybris.bcmc.data.browserInfo));
        }

        return true;
    }
}