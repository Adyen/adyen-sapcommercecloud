(function () {
    function getCsrf() {
        const tokenMeta = document.querySelector("meta[name='_csrf']");
        const headerMeta = document.querySelector("meta[name='_csrf_header']");

        return {
            token: tokenMeta ? tokenMeta.getAttribute("content") : null,
            header: headerMeta ? headerMeta.getAttribute("content") : null
        };
    }

    async function init() {
        const cfgNode = document.getElementById("adyen-myaccount-config");
        if (!cfgNode) {
            console.log("Missing #adyen-myaccount-config");
            return;
        }

        const clientKey = cfgNode.dataset.clientKey;
        const environment = cfgNode.dataset.environment;
        const locale = cfgNode.dataset.locale || "en_US";
        const countryCode = cfgNode.dataset.countryCode || "EN";

        if (!clientKey || !environment || !countryCode) {
            console.log("Missing Adyen config on My Account page", {
                clientKey,
                environment,
                locale,
                countryCode
            });
            return;
        }

        const checkout = await AdyenWeb.AdyenCheckout({
            clientKey,
            environment,
            locale,
            countryCode
        });

        let latestState = null;

        const saveButton = document.getElementById("adyen-tokenize-card-btn");
        const msgNode = document.getElementById("adyen-tokenize-card-msg");

        new AdyenWeb.Card(checkout, {
            type: "card",
            showPayButton: false,
            hasHolderName: true,
            holderNameRequired: true,
            storePaymentMethod: "askForConsent",
            onChange: (state) => {
                latestState = state;
                if (saveButton) {
                    saveButton.disabled = !(state && state.isValid);
                }
            }
        }).mount("#adyen-myaccount-card");

        if (saveButton) {
            saveButton.addEventListener("click", async function () {
                if (msgNode) {
                    msgNode.textContent = "";
                }

                if (!latestState || !latestState.isValid) {
                    if (msgNode) {
                        msgNode.textContent = "Card data is invalid.";
                    }
                    return;
                }

                const { token, header } = getCsrf();
                const paymentMethod = latestState.data.paymentMethod;

                const requestBody = {
                    paymentMethodDto: {
                        type: paymentMethod.type,
                        encryptedCardNumber: paymentMethod.encryptedCardNumber,
                        encryptedExpiryMonth: paymentMethod.encryptedExpiryMonth,
                        encryptedExpiryYear: paymentMethod.encryptedExpiryYear,
                        encryptedSecurityCode: paymentMethod.encryptedSecurityCode,
                        holderName: paymentMethod.holderName || null
                    }
                };

                $.ajax({
                    url: ACC.config.encodedContextPath + '/adyen/zero-auth',
                    type: 'POST',
                    data: JSON.stringify(requestBody),
                    contentType: "application/json; charset=utf-8",
                    beforeSend: function (xhr) {
                        if (token && header) {
                            xhr.setRequestHeader(header, token);
                        }
                    },
                    success: function (response) {
                        if (msgNode) {
                            msgNode.textContent = "Saved. Response: " + (typeof response === "string" ? response : JSON.stringify(response));
                        }
                    },
                    error: function (xhr) {
                        const msg = xhr.responseJSON || xhr.responseText || ('HTTP ' + xhr.status);
                        console.log("Zero-auth request failed", msg);
                        if (msgNode) {
                            msgNode.textContent = "Error: " + (typeof msg === "string" ? msg : JSON.stringify(msg));
                        }
                    }
                });
            });
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        init().catch(e => console.log("Adyen MyAccount init error", e));
    });
})();