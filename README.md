# Adyen SAP Commerce Cloud (Hybris) v6 plugin

Latest stable version:
https://github.com/Adyen/adyen-hybris/releases

This plugin supports SAP Commerce (Hybris) versions 2105

The plugin is using following adyen libraries and API.
- [adyen-java-api-library](https://github.com/Adyen/adyen-java-api-library) (v38.3.0)
- [adyen-web](https://github.com/Adyen/adyen-web) (v6.5.0)
- [Adyen Checkout API](https://docs.adyen.com/api-explorer/) (v71)

## Integration

The SAP Commerce integrates Adyen Checkout for all card payments and local/redirect payment methods.

## Requirements
SAP Commerce (Hybris) version 2105 or later

## Installation

### 1. Copy extension files to bin/custom directory

### 2. Add the Adyen extensions to the config/localextensions.xml file

#### Checkout

Required for accelerator checkout:
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6core"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6b2ccheckoutaddon"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6backoffice"/>
```

Required for headless checkout:
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6core"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyencheckoutaddonapi"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6backoffice"/>
```

Required for headless checkout api with example React SPA checkout:
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6core"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyencheckoutaddonapi"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyencheckoutaddonspa"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6backoffice"/>
```

#### Notifications

Required for the notifications:
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6notification"/>
```

Required for the event driven notifications:
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6notificationv2"/>
```

#### Order management

Additionally, required when using yacceleratorordermanagement (b2c_acc_oms recipe for 6.x and b2c_b2b_acc_oms recipe for 1905) :
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6ordermanagement"/>
```

#### Fulfilment

Additionally, required when using yacceleratorfulfilment (b2c_acc recipe for 6.x and b2c_acc_plus for 1905):
```
<extension dir="${HYBRIS_BIN_DIR}/custom/adyen-hybris/adyenv6fulfilmentprocess"/>
```

### 3. Modify local.properties

Modify config/local.properties file:

1. append ``` ,/[^/]+(/[^?]*)+(adyen-response)$,/adyen(/[^?]*)+$ ``` to the value of ```csrf.allowed.url.patterns```
2. add ```is3DS2allowed = true```


### 4. Build
```
cd bin/platform
. ./setantenv.sh
ant addoninstall -Daddonnames="adyenv6b2ccheckoutaddon" -DaddonStorefront.yacceleratorstorefront="yacceleratorstorefront"
ant clean all
```

### 5. Add your Adyen credentials to the BaseStore via Hybris backoffice

For more detailed instructions you can visit the [documentation page](https://github.com/Adyen/adyen-hybris/wiki)
Please make sure your merchant has Variant true in API and responses section so that you get paymentMethod back in response.

## React fronend build
1. Go to adyencheckoutaddonspa/acceleratoraddon/web/webroot/_ui/responsive/common/js/adyen-checkout
2. Open terminal and run `npm run i`
3. For development build run `npm run build`
4. For deployment build run `npm run build-prod`

## Supported payment methods
A reference of all supported payment methods is [here](https://github.com/Adyen/adyen-hybris/wiki#supported-payment-methods).

### Credit Cards

Credit Card payments are supported using Checkout Components.

### Apple Pay

Apple Pay is supported using Checkout Components.

### Ratepay

Ratepay is supported via Adyen API.

### AfterPay

AfterPay is supported via Adyen component and API.

### Boleto

Boleto is supported via Adyen API.

### Pix

Pix is supported via Adyen component and API.

### Other alternative payment methods

Supported via Adyen Checkout.


## Usage with OCC

The plugin supports the following OCC v2 compatible methods via [com.adyen.v6.facades.AdyenCheckoutFacade](adyenv6core/src/com/adyen/v6/facades/AdyenCheckoutFacade.java):

1. PaymentDetailsListWsDTO getPaymentDetails(String userId) throws IOException, ApiException;

```
    OCC controller: UsersController.getPaymentInfos
    Endpoint: GET /{userId}/paymentdetails
```

This method that will return the stored cards associated to the shopping cart user via Adyen API.

2. PaymentDetailsWsDTO addPaymentDetails(PaymentDetailsWsDTO paymentDetails, DataMapper dataMapper);

```
    OCC controller: CartsController.addPaymentDetails
    Endpoint: POST /{cartId}/paymentdetails
```

This method that will receive the payment method selection and rest of payment details and store them in the Cart.

For Credit Card payments - it expects encrypted card holder data obtained from your frontend implementation using Secured Fields

For Stored Cards payments - selected Adyen recurringReference of the card and encrypted cvc

For Boleto payments - social security number


3. OrderData authorisePayment(CartData cartData) throws Exception;

```
    OCC controller: OrdersController.placeOrder
    Endpoint: POST /users/{userId}/orders
```

This method will place the payment request using the previously stored payment method selection data. Upon successful response from Adyen API, it will register payment response in cart/order level.

It returns an instance of OrderWSDTO obtained from OrderData of the placed order.
For Boleto, it will contain the pdf url, the base64 encoded data, expiration date and due date.

## 3DS2 configuration
By default 3DS2 is enabled (Except for OCC). If you want to disable 3DS2 in your system, please set following property in local.properties file, build your environment and restart the server.
```
is3DS2allowed = false
```
## POS Timeout configuration
POS timeout (time calculated since initiating a payment) is max time to keep terminal connection open. It is set to 130 seconds by default already. If you want to change it, please add following property in local.properties file, build your environment and restart the server. (Change 130 to your desired time, in seconds).
```
pos.totaltimeout = 130
```
## Credit card holder name configuration
By default Credit card holder name is a mandatory field, You can disable it by setting following property in `local.properties` file.
```
isCardHolderNameRequired = false
```

## Pending Order Timeout configuration
By default, an order remains in PAYMENT_PENDING status in order management for 1 hour and it is configured in dynamic order process defintiion file.
Based on which extension you are using (fulfillment or ordermanangement) timeout value can be updated in corresponding order-process.xml file.

For example, following 2 files have 60 mins configuration under waitForAdyenPendingPayment process with delay value=PT60M

Fulfillment extension file - resources/adyenv6fulfilmentprocess/process/order-process.xml

OrderManagement extension file - resources/adyenv6ordermanagement/process/order-process.xml
```
<wait id="waitForAdyenPendingPayment" then="checkPendingOrder">
        <event>AdyenPaymentResult</event>
        <timeout delay="PT60M" then="checkPendingOrder"/>
    </wait>
```

## PayPal configuration
This plugin uses Adyen's Checkout Component for PayPal payments. To use that in a live environment, a PayPal Merchant Id is required. This id has to be provided when adding your Adyen credentials to the BaseStore via the backoffice [(installation step 5)](#installation).

## SameSite Cookie Handler configuration
On Google Chrome browser versions 80 or later, it might occur that an account is logged out after trying to place an order using a credit card that requires 3D Secure authentication or using other redirect payment methods.
This is a consequence of how newer versions of Chrome browsers handle the [SameSite attribute](https://web.dev/samesite-cookies-explained/) on cookies, invalidating the user session after a redirect to a third-party page happened.

To avoid those issues, for SAP Commerce versions 6.x or 1905, a cookie handler included in this plugin can be used. To enable it, add the following configuration to the config/local.properties file:

```
adyen.samesitecookie.handler.enabled=true
```

For SAP Commerce versions 2005 and above, check how to use [SAP's SameSite Cookie Attribute Handler](https://help.sap.com/viewer/d0224eca81e249cb821f2cdf45a82ace/2005/en-US/bde41b6a42c541a08eb2a3b1993fb097.html).

## Amazon Pay configuration
To be able to complete payments in Amazon Pay, you must add your Amazon Pay private key to the following folder /adyen-hybris/adyenv6core/resources/certificates, and once done, set the correct PEM file name on /adyen-hybris/adyenv6core/src/com/adyen/v6/DefaultAdyenAmazonPayIntegratorService.java in the following line:
```
payConfiguration = new PayConfiguration()
                    .setPublicKeyId(amazonpayPublicKey)
                    .setRegion(Region.valueOf(amazonpayRegion.getCode()))
--->                .setPrivateKey(new String(Files.readAllBytes(ResourceUtils.getFile("classpath:certificates/amazonpay/YourCertificateName.pem").toPath())).toCharArray())
```

## Multiple merchant configuration
Introduces possibility to select merchant for given transaction based on strategy that could be extended. By default merchant account is one in adyenMerchantAccount on BaseStore.
### Example
In /examples/adyenmultiplemerchantexample there is extension that introduces necessary changes to achieve selection of merchant account based on shipping address country selected during checkout process.

 ## Documentation
 https://github.com/Adyen/adyen-hybris/wiki

 ## Support
If you have a feature request, or spotted a bug or a technical problem, create a GitHub issue. For other questions, contact our [support team](https://support.adyen.com/hc/en-us/requests/new?ticket_form_id=360000705420).

 ## Contributing
 We strongly encourage you to join us in contributing to this repository so everyone can benefit from:
 * New features and functionality
 * Resolved bug fixes and issues
 * Any general improvements

 Read our [**contribution guidelines**](CONTRIBUTING.md) to find out how.

 ## License
 MIT license. For more information, see the LICENSE file.
