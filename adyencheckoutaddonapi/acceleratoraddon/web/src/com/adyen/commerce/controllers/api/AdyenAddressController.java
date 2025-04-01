package com.adyen.commerce.controllers.api;

import com.adyen.commerce.exception.AdyenControllerException;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.forms.AddressForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.validation.AddressValidator;
import de.hybris.platform.acceleratorstorefrontcommons.util.AddressDataUtil;
import de.hybris.platform.commercefacades.address.AddressVerificationFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.address.AddressVerificationDecision;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.adyen.commerce.util.FieldValidationUtil.getFieldCodesFromValidation;

@Controller
@RequestMapping(value = "/api/account")
public class AdyenAddressController {

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private AddressVerificationFacade addressVerificationFacade;

    @Resource(name = "addressValidator")
    private AddressValidator addressValidator;

    @Resource(name = "addressDataUtil")
    private AddressDataUtil addressDataUtil;

    @Autowired
    private CheckoutFacade checkoutFacade;

    @Resource(name = "checkoutCustomerStrategy")
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @RequireHardLogIn
    @GetMapping(value = "/delivery-address", produces = "application/json")
    public ResponseEntity<List<AddressData>> getAllDeliveryAddresses() {
        List<AddressData> addressDataList = userFacade.getAddressBook();

        return ResponseEntity.status(HttpStatus.OK).body(addressDataList);
    }

    @RequireHardLogIn
    @PostMapping(value = "/delivery-address")
    public ResponseEntity<Void> addDeliveryAddress(@RequestBody AddressForm addressForm) {
        final Errors errors = new BeanPropertyBindingResult(addressForm, "address");
        addressValidator.validate(addressForm, errors);
        if (errors.hasErrors()) {
            throw new AdyenControllerException("checkout.deliveryAddress.notSelected", getFieldCodesFromValidation(errors));
        }

        AddressData addressData = addressDataUtil.convertToAddressData(addressForm);

        processAddressVisibilityAndDefault(addressForm, addressData);

        if (addressVerificationFacade.verifyAddressData(addressData).getDecision().equals(AddressVerificationDecision.ACCEPT)) {
            addressData.setVisibleInAddressBook(true);
            userFacade.addAddress(addressData);

            final AddressData previousSelectedAddress = checkoutFacade.getCheckoutCart().getDeliveryAddress();
            // Set the new address as the selected checkout delivery address
            checkoutFacade.setDeliveryAddress(addressData);
            if (previousSelectedAddress != null && !previousSelectedAddress.isVisibleInAddressBook()) { // temporary address should be removed
                userFacade.removeAddress(previousSelectedAddress);
            }

            // Set the new address as the selected checkout delivery address
            checkoutFacade.setDeliveryAddress(addressData);

            return ResponseEntity.status(HttpStatus.CREATED).build();
        } else {
            throw new AdyenControllerException();
        }
    }

    @RequireHardLogIn
    @PutMapping(value = "/delivery-address")
    public ResponseEntity<Void> updateDeliveryAddress(@RequestBody AddressForm addressForm) {
        final Errors errors = new BeanPropertyBindingResult(addressForm, "address");
        addressValidator.validate(addressForm, errors);
        if (errors.hasErrors()) {
            throw new AdyenControllerException("checkout.deliveryAddress.notSelected", getFieldCodesFromValidation(errors));
        }

        AddressData addressData = addressDataUtil.convertToAddressData(addressForm);


        if (addressVerificationFacade.verifyAddressData(addressData).getDecision().equals(AddressVerificationDecision.ACCEPT)) {
            if (userFacade.getAddressBook().stream().anyMatch(ad -> Objects.equals(ad.getId(), addressData.getId()))) {
                addressData.setVisibleInAddressBook(true);
                userFacade.editAddress(addressData);

                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                throw new AdyenControllerException();
            }
        } else {
            throw new AdyenControllerException();
        }
    }

    @RequireHardLogIn
    @DeleteMapping(value = "/delivery-address/{addressId}")
    public ResponseEntity<Void> removeDeliveryAddress(@PathVariable String addressId) {
        if (userFacade.getAddressBook().stream().anyMatch(ad -> Objects.equals(ad.getId(), addressId))) {
            AddressData addressData = userFacade.getAddressForCode(addressId);

            userFacade.removeAddress(addressData);
            addressData.setVisibleInAddressBook(false);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } else {
            throw new AdyenControllerException();
        }
    }

    private void processAddressVisibilityAndDefault(final AddressForm addressForm, final AddressData newAddress) {
        if (addressForm.getSaveInAddressBook() != null) {
            newAddress.setVisibleInAddressBook(addressForm.getSaveInAddressBook().booleanValue());
            if (addressForm.getSaveInAddressBook().booleanValue() && CollectionUtils.isEmpty(userFacade.getAddressBook())) {
                newAddress.setDefaultAddress(true);
            }
        } else if (checkoutCustomerStrategy.isAnonymousCheckout()) {
            newAddress.setDefaultAddress(true);
            newAddress.setVisibleInAddressBook(true);
        }
    }
}
