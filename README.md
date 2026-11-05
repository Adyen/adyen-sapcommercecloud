# Adyen SAP Commerce Cloud plugin

The Adyen plugin connects SAP Commerce Cloud to [Adyen](https://www.adyen.com). It provides the Adyen payment experience (Drop-in and Components) for the Accelerator storefront and for a React checkout, OCC endpoints for the Composable Storefront (Spartacus), webhook processing that drives the order process, and Backoffice configuration. Supported features include card payments with 3D Secure 2, local and redirect payment methods, express checkout, stored cards, gift cards with partial payments, Adyen Giving, and subscription billing with Recurly and Chargebee.

## Compatibility

| Component | Version |
|---|---|
| Plugin | 13.6.0 |
| SAP Commerce | 2211-jdk21 (Java 21, Spring 6) |
| [adyen-java-api-library](https://github.com/Adyen/adyen-java-api-library) | 42.0.1 |
| [Adyen Checkout API](https://docs.adyen.com/api-explorer/) | v71 |
| [Adyen Web](https://github.com/Adyen/adyen-web) | 6.40.1 in the Accelerator storefront (loaded from the Adyen CDN), `^6.46.0` in the React checkout |
| [`@adyen/adyen-spartacus`](https://github.com/Adyen/adyen-spartacus) | 1.6.0 (Angular 21, Spartacus 221121.7.0), versioned separately |

## What is included

| Group | Extensions |
|---|---|
| Core | `adyenv6core` (payment logic, services, order process actions, base store attributes), `adyenwebcommons` (shared controller bases), `adyenv6backoffice` (Backoffice configuration and order actions) |
| Webhooks | `adyenv6notificationv2` (event-driven, recommended), `adyenv6notification` (older, cron job based) |
| Order process | `adyenv6fulfilmentprocess` (standard Accelerator fulfilment), `adyenv6ordermanagement` (Order Management) |
| Storefront | `adyenv6b2ccheckoutaddon` (Accelerator checkout), `adyencheckoutaddonapi` and `adyencheckoutaddonspa` (React checkout), `adyenocc` (OCC for the Composable Storefront) |
| Subscriptions | `adyensubscriptionconnector`, `adyenrecurlyconnector`, `adyenchargebeeconnector`, `adyensubscriptionb2caddon` (billing platforms and My Subscriptions), `adyenv6subscription` (recurring orders for SAP Commerce subscription products) |
| SAP Digital Payments | `adyensapdigitalpayment`, `adyensapdigitalpaymentbackoffice` |
| Examples | `examples/adyenmultiplemerchantexample`, `examples/adyenv6consignmentpartialcaptureexample` |

## Quick start

1. Download the release from [GitHub Releases](https://github.com/Adyen/adyen-sapcommercecloud/releases) and unpack it into `hybris/bin/custom/adyen-sapcommercecloud`.
2. Add the extensions to `config/localextensions.xml`: `adyenv6core`, `adyenwebcommons`, `adyenv6backoffice`, `adyenv6notificationv2`, an order process extension, and the storefront extension for your setup (`adyenv6b2ccheckoutaddon`, `adyencheckoutaddonapi` with `adyencheckoutaddonspa`, or `adyenocc`).
3. Set `is3DS2allowed=true` in `config/local.properties`, plus the CSRF patterns (Accelerator and React checkout) or the OCC and storefront URLs (Composable Storefront).
4. For the Accelerator storefront and the React checkout, run `ant addoninstall` for the add-ons. For the React checkout, also build the bundle with `npm run build-prod`.
5. Run `ant clean all`, then initialize or update the system.
6. In Backoffice, enter your Adyen credentials on the base store and set its checkout flow group (`adyenB2CResponsiveCheckoutGroup` or `adyenB2CReactCheckoutGroup`). In the Adyen Customer Area, create the webhook.

The [Installation](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Installation) page describes each step in detail, including the Adyen account preparation and a go-live checklist.

## Documentation

The documentation is in the [wiki](https://github.com/Adyen/adyen-sapcommercecloud/wiki):

- [Installation](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Installation)
- [Upgrading](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Upgrading)
- [Configuration](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Configuration)
- [Payment Methods and Features](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Payment-Methods-and-Features)
- [Subscription Billing](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Subscription-Billing)
- [Composable Storefront](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Composable-Storefront)
- [API Reference](https://github.com/Adyen/adyen-sapcommercecloud/wiki/API)
- [Troubleshooting](https://github.com/Adyen/adyen-sapcommercecloud/wiki/Troubleshooting)

Release notes are on the [Releases](https://github.com/Adyen/adyen-sapcommercecloud/releases) page.

## Support

If you have a feature request, or spotted a bug or a technical problem, create a GitHub issue. For other questions, contact the [Adyen support team](https://support.adyen.com/hc/en-us/requests/new?ticket_form_id=360000705420).

## Contributing

We encourage you to contribute to this repository, so everyone can benefit from new features, bug fixes and general improvements. Read the [contribution guidelines](CONTRIBUTING.md) to find out how.

## License

MIT license. For more information, see the [LICENSE](LICENSE) file.
