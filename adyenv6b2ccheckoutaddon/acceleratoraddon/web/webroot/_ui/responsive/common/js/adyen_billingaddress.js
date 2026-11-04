$( document ).ready( function () {

    var spinner = $( "<img src='" + ACC.config.commonResourcePath + "/images/spinner.gif' />" );
    var disclaimerRequestId = 0;

    function getEffectiveBillingCountry () {
        if ( $( '#useAdyenDeliveryAddress' ).is( ":checked" ) ) {
            return $( '#useAdyenDeliveryAddressData' ).data( 'countryisocode' );
        }
        return $( '#address\\.country' ).val();
    }

    function updateDisclaimer (message, link) {
        var safeLink = typeof link === 'string' && /^https?:\/\//i.test( link ) ? link : '';
        $( '.adyen-disclaimer' ).each( function () {
            var disclaimer = $( this );
            disclaimer.find( '.adyen-disclaimer__message' ).text( message || '' );
            var linkElement = disclaimer.find( '.adyen-disclaimer__link' );
            if ( safeLink ) {
                linkElement.attr( 'href', safeLink ).show();
            } else {
                linkElement.removeAttr( 'href' ).hide();
            }
            if ( message ) {
                disclaimer.show();
            } else {
                disclaimer.hide();
            }
        } );
    }

    function refreshDisclaimer (countryCode) {
        var requestId = ++disclaimerRequestId;
        if ( !countryCode ) {
            updateDisclaimer( '', '' );
            return;
        }

        updateDisclaimer( '', '' );
        $.ajax( {
            url: ACC.config.encodedContextPath + '/checkout/multi/adyen/select-payment-method/disclaimer-configuration',
            type: 'GET',
            data: {shopperCountryCode: countryCode},
            dataType: 'json'
        } ).done( function (configuration) {
            if ( requestId === disclaimerRequestId ) {
                updateDisclaimer( configuration.disclaimerMessage, configuration.disclaimerLink );
            }
        } );
    }

    function binduseAdyenDeliveryAddress () {
        $( '#useAdyenDeliveryAddress' ).on( 'change', function () {
            if ( $( '#useAdyenDeliveryAddress' ).is( ":checked" ) ) {
                var options = {'countryIsoCode': $( '#useAdyenDeliveryAddressData' ).data( 'countryisocode' ), 'useAdyenDeliveryAddress': true};
                enableAddressForm();
                displayAdyenCreditCardAddressForm( options, onDeliveryAddressToggle );
                disableAddressForm();
            } else {
                clearAddressForm();
                enableAddressForm();
                refreshDisclaimer( getEffectiveBillingCountry() );
            }
        } );

        if ( $( '#useAdyenDeliveryAddress' ).is( ":checked" ) ) {
            var options = {'countryIsoCode': $( '#useAdyenDeliveryAddressData' ).data( 'countryisocode' ), 'useAdyenDeliveryAddress': true};
            enableAddressForm();
            displayAdyenCreditCardAddressForm( options, onDeliveryAddressToggle );
            disableAddressForm();
        }
    }

    function bindSubmitSilentOrderPostForm () {
        $( '.submit_silentOrderPostForm' ).click( function () {
            ACC.common.blockFormAndShowProcessingMessage( $( this ) );
            $( '.adyenBillingAddressForm' ).filter( ":hidden" ).remove();
            enableAddressForm();
            $( '#silentOrderPostForm' ).submit();
        } );
    }

    function disableAddressForm () {
        $( 'input[id^="address\\."]' ).prop( 'disabled', true );
        $( 'select[id^="address\\."]' ).prop( 'disabled', true );
    }

    function enableAddressForm () {
        $( 'input[id^="address\\."]' ).prop( 'disabled', false );
        $( 'select[id^="address\\."]' ).prop( 'disabled', false );
    }

    function clearAddressForm () {
        $( 'input[id^="address\\."]' ).val( "" );
        $( 'select[id^="address\\."]' ).val( "" );
    }

    function onDeliveryAddressToggle () {
        if ( $( '#useAdyenDeliveryAddress' ).is( ":checked" ) ) {
            $( '#address\\.country' ).val( $( '#useAdyenDeliveryAddressData' ).data( 'countryisocode' ) );
            disableAddressForm();
            refreshDisclaimer( getEffectiveBillingCountry() );
        } else {
            clearAddressForm();
            enableAddressForm();
            refreshDisclaimer( getEffectiveBillingCountry() );
        }
    }

    function bindCreditCardAddressForm () {
        $( '#billingAdyenCountrySelector :input' ).on( "change", function () {
            var countrySelection = $( this ).val();
            var options = {
                'countryIsoCode': countrySelection,
                'useAdyenDeliveryAddress': false
            };
            displayAdyenCreditCardAddressForm( options );
            refreshDisclaimer( countrySelection );
        } )
    }

    function displayAdyenCreditCardAddressForm ( options, callback ) {
        const form = $( "#adyenBillingAddressForm" );
        $.ajax( {
            url: ACC.config.encodedContextPath + '/checkout/multi/adyen/select-payment-method/billingaddressform',
            async: true,
            data: options,
            dataType: "html",
            beforeSend: function () {
                form.html( spinner );
            }
        } ).done( function ( data ) {
            form.html( data );
            if ( typeof callback === 'function' ) {
                callback();
            }
        } );
    }

    binduseAdyenDeliveryAddress();
    bindSubmitSilentOrderPostForm();
    bindCreditCardAddressForm();
    if ( !$( '#useAdyenDeliveryAddress' ).is( ":checked" ) ) {
        refreshDisclaimer( getEffectiveBillingCountry() );
    }

} );
