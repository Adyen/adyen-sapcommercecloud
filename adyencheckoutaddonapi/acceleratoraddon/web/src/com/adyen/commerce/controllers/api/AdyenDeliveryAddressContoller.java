package com.adyen.commerce.controllers.api;


import com.adyen.commerce.exception.AdyenControllerException;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;

@Controller
@RequestMapping(value = "/api/checkout")
public class AdyenDeliveryAddressContoller {

    @Resource(name = "checkoutFacade")
    private CheckoutFacade checkoutFacade;

    @Resource(name = "userFacade")
    private UserFacade userFacade;

    @PostMapping(value = "/delivery-address")
    @RequireHardLogIn
    public ResponseEntity<Void> doSelectDeliveryAddress(@RequestBody final String selectedAddressCode) {
        if (StringUtils.isNotBlank(selectedAddressCode)) {
            final AddressData selectedAddressData = getCheckoutFacade().getDeliveryAddressForCode(selectedAddressCode);
            final boolean hasSelectedAddressData = selectedAddressData != null;
            if (hasSelectedAddressData) {
                setDeliveryAddress(selectedAddressData);
                return ResponseEntity.status(HttpStatus.ACCEPTED).build();
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        throw new AdyenControllerException("checkout.deliveryAddress.notSelected");
    }

    @GetMapping(value = "/delivery-address")
    @RequireHardLogIn
    public ResponseEntity<AddressData> getSelectedDeliveryAddress() {
        final AddressData selectedAddressData = getCheckoutFacade().getCheckoutCart().getDeliveryAddress();
        if (selectedAddressData != null) {
            return ResponseEntity.status(HttpStatus.OK).body(selectedAddressData);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    protected void setDeliveryAddress(final AddressData selectedAddressData) {
        final AddressData cartCheckoutDeliveryAddress = getCheckoutFacade().getCheckoutCart().getDeliveryAddress();
        if (isAddressIdChanged(cartCheckoutDeliveryAddress, selectedAddressData)) {
            getCheckoutFacade().setDeliveryAddress(selectedAddressData);
            if (cartCheckoutDeliveryAddress != null && !cartCheckoutDeliveryAddress.isVisibleInAddressBook()) { // temporary address should be removed
                getUserFacade().removeAddress(cartCheckoutDeliveryAddress);
            }
        }
    }

    protected boolean isAddressIdChanged(final AddressData cartCheckoutDeliveryAddress, final AddressData selectedAddressData) {
        return cartCheckoutDeliveryAddress == null || !selectedAddressData.getId().equals(cartCheckoutDeliveryAddress.getId());
    }


    public UserFacade getUserFacade() {
        return userFacade;
    }

    public void setUserFacade(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    public CheckoutFacade getCheckoutFacade() {
        return checkoutFacade;
    }

    public void setCheckoutFacade(CheckoutFacade checkoutFacade) {
        this.checkoutFacade = checkoutFacade;
    }
}
