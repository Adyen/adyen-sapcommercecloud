package com.adyen.commerce.controllers.checkout;

import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateQuoteCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.checkout.steps.AbstractCheckoutStepController;
import de.hybris.platform.acceleratorstorefrontcommons.forms.AddressForm;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.adyen.commerce.constants.AdyencheckoutaddonspaWebConstants.ADYEN_CHECKOUT_ORDER_CONFIRMATION;
import static com.adyen.commerce.constants.AdyencheckoutaddonspaWebConstants.ADYEN_CHECKOUT_PAGE_PREFIX;


@Controller
@RequestMapping(value = ADYEN_CHECKOUT_PAGE_PREFIX)
public class AdyenPageCheckoutStepController extends AbstractCheckoutStepController {
    private static final String DELIVERY_ADDRESS = "delivery-address";
    private static final String SHOW_SAVE_TO_ADDRESS_BOOK_ATTR = "showSaveToAddressBook";
    public static final String SPA_CHECKOUT_PAGE = "addon:/adyencheckoutaddonspa/pages/adyenSPACheckout";


    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @GetMapping(value = "/adyen/**")
    @RequireHardLogIn
    @PreValidateQuoteCheckoutStep
    @PreValidateCheckoutStep(checkoutStep = DELIVERY_ADDRESS)
    public String enterStep(final Model model, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException {
        getCheckoutFacade().setDeliveryAddressIfAvailable();
        final CartData cartData = getCheckoutFacade().getCheckoutCart();

        populateCommonModelAttributes(model, cartData, new AddressForm());

        return SPA_CHECKOUT_PAGE;
    }

    @GetMapping(value = "/adyen/payment-method/error/*")
    @RequireHardLogIn
    @PreValidateQuoteCheckoutStep
    @PreValidateCheckoutStep(checkoutStep = DELIVERY_ADDRESS)
    public String enterPaymentStepWithError(final Model model, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();

        populateCommonModelAttributes(model, cartData, new AddressForm());

        return SPA_CHECKOUT_PAGE;
    }

    @GetMapping(value = {ADYEN_CHECKOUT_ORDER_CONFIRMATION, ADYEN_CHECKOUT_ORDER_CONFIRMATION + "/**"})
    @RequireHardLogIn
    public String enterOrderConfirmationStep(final Model model) throws CMSItemNotFoundException {
        final ContentPageModel multiCheckoutSummaryPage = getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL);
        storeCmsPageInModel(model, multiCheckoutSummaryPage);
        setUpMetaDataForContentPage(model, multiCheckoutSummaryPage);

        return SPA_CHECKOUT_PAGE;
    }

    protected void populateCommonModelAttributes(final Model model, final CartData cartData, final AddressForm addressForm)
            throws CMSItemNotFoundException {
        model.addAttribute("cartData", cartData);
        model.addAttribute("addressForm", addressForm);
        model.addAttribute("deliveryAddresses", getDeliveryAddresses(cartData.getDeliveryAddress()));
        model.addAttribute("noAddress", getCheckoutFlowFacade().hasNoDeliveryAddress());
        model.addAttribute("addressFormEnabled", getCheckoutFacade().isNewAddressEnabledForCart());
        model.addAttribute("removeAddressEnabled", getCheckoutFacade().isRemoveAddressEnabledForCart());
        model.addAttribute(SHOW_SAVE_TO_ADDRESS_BOOK_ATTR, Boolean.TRUE);
        model.addAttribute(WebConstants.BREADCRUMBS_KEY, getResourceBreadcrumbBuilder().getBreadcrumbs(getBreadcrumbKey()));
        model.addAttribute("metaRobots", "noindex,nofollow");
        model.addAttribute(DefaultAdyenCheckoutFacade.MODEL_CHECKOUT_SHOPPER_HOST, adyenCheckoutFacade.getCheckoutShopperHost());
        if (StringUtils.isNotBlank(addressForm.getCountryIso())) {
            model.addAttribute("regions", getI18NFacade().getRegionsForCountryIso(addressForm.getCountryIso()));
            model.addAttribute("country", addressForm.getCountryIso());
        }
        prepareDataForPage(model);
        final ContentPageModel multiCheckoutSummaryPage = getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL);
        storeCmsPageInModel(model, multiCheckoutSummaryPage);
        setUpMetaDataForContentPage(model, multiCheckoutSummaryPage);
        setCheckoutStepLinksForModel(model, getCheckoutStep());
    }

    protected String getBreadcrumbKey() {
        return "checkout.multi." + getCheckoutStep().getProgressBarId() + ".breadcrumb";
    }

    protected CheckoutStep getCheckoutStep() {
        return getCheckoutStep(DELIVERY_ADDRESS);
    }

    @Override
    public String back(RedirectAttributes redirectAttributes) {
        return null;
    }

    @Override
    public String next(RedirectAttributes redirectAttributes) {
        return null;
    }
}
