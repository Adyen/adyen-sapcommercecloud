/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.controllers.pages.checkout.steps;

import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.AdyenControllerConstants;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.forms.AdyenPaymentForm;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.checkout.steps.AbstractCheckoutStepController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.TitleData;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static com.adyen.v6.constants.AdyenControllerConstants.Views.Pages.MultiStepCheckout.BillingAddressformPage;
import static de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants.BREADCRUMBS_KEY;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import com.adyen.service.exception.ApiException;

@Controller
@RequestMapping(value = "/checkout/multi/adyen/select-payment-method")
public class SelectPaymentMethodCheckoutStepController extends AbstractCheckoutStepController {
    private static final Logger LOGGER = Logger.getLogger(SelectPaymentMethodCheckoutStepController.class);

    protected static final String PAYMENT_METHOD_STEP_NAME = "payment-method";

    protected static final String CHECKOUT_MULTI_PAYMENT_METHOD_BREADCRUMB = "checkout.multi.paymentMethod.breadcrumb";

    protected static final String ADYEN_PAYMENT_FORM = "adyenPaymentForm";
    protected static final String CSE_GENERATION_TIME = "generationTime";

    private static final String CART_DATA_ATTR = "cartData";

    @Resource(name = "adyenCheckoutFacade")
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "userFacade")
    private UserFacade userFacade;

    @ModelAttribute("billingCountries")
    public Collection<CountryData> getBillingCountries() {
        return getCheckoutFacade().getBillingCountries();
    }

    @ModelAttribute("titles")
    public Collection<TitleData> getBillingTitleCodes() {
        return getUserFacade().getTitles();
    }

    protected UserFacade getUserFacade() {
        return userFacade;
    }

    @Autowired
    private HttpServletRequest httpServletRequest;

    /**
     * {@inheritDoc}
     */
    @Override
    @RequestMapping(value = "", method = GET)
    @RequireHardLogIn
    //    @PreValidateCheckoutStep (checkoutStep = PAYMENT_METHOD_STEP_NAME)
    public String enterStep(final Model model, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();

        model.addAttribute("metaRobots", "noindex,nofollow");
        model.addAttribute("hasNoPaymentInfo", getCheckoutFlowFacade().hasNoPaymentInfo());
        model.addAttribute(BREADCRUMBS_KEY, getResourceBreadcrumbBuilder().getBreadcrumbs(CHECKOUT_MULTI_PAYMENT_METHOD_BREADCRUMB));
        if (! model.containsAttribute(ADYEN_PAYMENT_FORM)) {
            model.addAttribute(ADYEN_PAYMENT_FORM, new AdyenPaymentForm());
        }
        model.addAttribute(CSE_GENERATION_TIME, fromCalendar(Calendar.getInstance()));
        model.addAttribute(CART_DATA_ATTR, cartData);
        model.addAttribute("deliveryAddress", cartData.getDeliveryAddress());
        model.addAttribute("expiryYears", getExpiryYears());

        if(isSessionCartInvalid())
        {
            LOGGER.warn("Invalid cart found in session, cart code: " + cartData.getCode());
            GlobalMessages.addErrorMessage(model, "checkout.deliveryAddress.notSelected");
        }
        else {
            try{
                adyenCheckoutFacade.initializeCheckoutData(model);
            }
            catch(ApiException e){
                LOGGER.warn("Invalid session Data");
                GlobalMessages.addErrorMessage(model, "basket.error.occurred");
            }

        }

        super.prepareDataForPage(model);

        final ContentPageModel contentPage = getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL);
        super.storeCmsPageInModel(model, contentPage);
        super.setUpMetaDataForContentPage(model, contentPage);
        super.setCheckoutStepLinksForModel(model, getCheckoutStep());

        return AdyenControllerConstants.Views.Pages.MultiStepCheckout.SelectPaymentMethod;
    }


    @RequestMapping(value = "/billingaddressform", method = RequestMethod.GET)
    public String getCountryAddressForm(@RequestParam("countryIsoCode") final String countryIsoCode,
                                        @RequestParam("useAdyenDeliveryAddress") final boolean useAdyenDeliveryAddress,
                                        final Model model) {

        model.addAttribute("supportedCountries", getCountries());
        model.addAttribute("regions", getI18NFacade().getRegionsForCountryIso(countryIsoCode));
        model.addAttribute("country", countryIsoCode);

        final AdyenPaymentForm adyenPaymentForm = new AdyenPaymentForm();
        AddressForm addressForm = new AddressForm();

        if (useAdyenDeliveryAddress) {
            final AddressData deliveryAddress = getCheckoutFacade().getCheckoutCart().getDeliveryAddress();
            if (deliveryAddress != null && deliveryAddress.getRegion() != null && ! StringUtils.isEmpty(deliveryAddress.getRegion().getIsocode())) {
                addressForm.setRegionIso(deliveryAddress.getRegion().getIsocodeShort());
            }
            addressForm.setTitleCode(deliveryAddress.getTitleCode());
            addressForm.setFirstName(deliveryAddress.getFirstName());
            addressForm.setLastName(deliveryAddress.getLastName());
            addressForm.setLine1(deliveryAddress.getLine1());
            addressForm.setLine2(deliveryAddress.getLine2());
            addressForm.setTownCity(deliveryAddress.getTown());
            addressForm.setPostcode(deliveryAddress.getPostalCode());
            addressForm.setCountryIso(deliveryAddress.getCountry().getIsocode());
            addressForm.setPhoneNumber(deliveryAddress.getPhone());
        }
        adyenPaymentForm.setBillingAddress(addressForm);
        model.addAttribute("adyenPaymentForm", adyenPaymentForm);
        return BillingAddressformPage;
    }


    @RequestMapping(value = "", method = POST)
    @RequireHardLogIn
    public String setPaymentMethod(final Model model,
                                   final RedirectAttributes redirectAttributes,
                                   @Valid final AdyenPaymentForm adyenPaymentForm,
                                   final BindingResult bindingResult) throws CMSItemNotFoundException {
        LOGGER.debug("PaymentForm: " + adyenPaymentForm);

        if (isSessionCartInvalid()) {
            GlobalMessages.addErrorMessage(model, "checkout.error.paymentethod.formentry.invalid");
            if (adyenPaymentForm.getBillingAddress() != null) {
                adyenPaymentForm.resetFormExceptBillingAddress();
            }
            return enterStep(model, redirectAttributes);
        }

        adyenCheckoutFacade.handlePaymentForm(adyenPaymentForm, bindingResult);

        if (bindingResult.hasGlobalErrors()|| bindingResult.hasErrors()|| isSessionCartInvalid()) {
            LOGGER.debug(bindingResult.getAllErrors().stream().map(error -> (error.getCode())).reduce((x, y) -> (x = x + y)));
            GlobalMessages.addErrorMessage(model, "checkout.error.paymentethod.formentry.invalid");
            if (adyenPaymentForm.getBillingAddress() != null) {
               adyenPaymentForm.resetFormExceptBillingAddress();
            }
            return enterStep(model, redirectAttributes);
        }

        setCheckoutStepLinksForModel(model, getCheckoutStep());

        return getCheckoutStep().nextStep();
    }

    private String fromCalendar(final Calendar calendar) {
        final Date date = calendar.getTime();
        final String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date);
        return formatted.substring(0, 22) + ":" + formatted.substring(22);
    }

    /**
     * Returns a list with CC expiry years
     */
    public List<String> getExpiryYears() {
        List<String> expiryYears = new ArrayList<>();
        final Calendar calender = new GregorianCalendar();

        for (int i = calender.get(Calendar.YEAR); i < calender.get(Calendar.YEAR) + 11; i++) {
            expiryYears.add(String.valueOf(i));
        }

        return expiryYears;
    }

    /**
     * {@inheritDoc}
     */
    @RequestMapping(value = "/back", method = GET)
    @RequireHardLogIn
    @Override
    public String back(final RedirectAttributes redirectAttributes) {
        return getCheckoutStep().previousStep();
    }

    /**
     * {@inheritDoc}
     */
    @RequestMapping(value = "/next", method = GET)
    @RequireHardLogIn
    @Override
    public String next(final RedirectAttributes redirectAttributes) {
        return getCheckoutStep().nextStep();
    }

    /**
     * {@inheritDoc}
     */
    protected CheckoutStep getCheckoutStep() {
        return getCheckoutStep(PAYMENT_METHOD_STEP_NAME);
    }

    public AdyenCheckoutFacade getAdyenCheckoutFacade() {
        return adyenCheckoutFacade;
    }

    public void setAdyenCheckoutFacade(AdyenCheckoutFacade adyenCheckoutFacade) {
        this.adyenCheckoutFacade = adyenCheckoutFacade;
    }

    private boolean isSessionCartInvalid() {
        CartData cart = getCheckoutFacade().getCheckoutCart();
        final AddressData deliveryAddress = cart.getDeliveryAddress();
        return (deliveryAddress == null || deliveryAddress.getCountry()==null || deliveryAddress.getCountry().getIsocode() == null);
    }
}
