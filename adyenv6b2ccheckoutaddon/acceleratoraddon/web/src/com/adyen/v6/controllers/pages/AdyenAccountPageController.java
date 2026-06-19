package com.adyen.v6.controllers.pages;

import com.adyen.v6.constants.AdyenControllerConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.forms.AddressForm;
import de.hybris.platform.acceleratorstorefrontcommons.util.AddressDataUtil;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.TitleData;
import de.hybris.platform.commerceservices.enums.CountryType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.annotation.Resource;
import java.util.Collection;

@Controller
@RequestMapping("adyen/my-account")
public class AdyenAccountPageController extends AbstractSearchPageController {

    private static final String COUNTRY_ATTR = "country";
    private static final String REGIONS_ATTR = "regions";
    private static final String ADDRESS_FORM_ATTR = "addressForm";
    private static final String ADDRESS_DATA_ATTR = "addressData";

    @Resource(name = "i18NFacade")
    private I18NFacade i18NFacade;

    @Resource(name = "userFacade")
    private UserFacade userFacade;

    @Resource(name = "addressDataUtil")
    private AddressDataUtil addressDataUtil;


    @Resource(name = "acceleratorCheckoutFacade")
    private CheckoutFacade checkoutFacade;

    @RequestMapping(value = "/addressform", method = RequestMethod.GET)
    public String getCountryAddressForm(@RequestParam("addressCode") final String addressCode,
                                        @RequestParam("countryIsoCode") final String countryIsoCode, final Model model)
    {
        model.addAttribute("supportedCountries", getCountries());
        model.addAttribute(REGIONS_ATTR, i18NFacade.getRegionsForCountryIso(countryIsoCode));
        model.addAttribute(COUNTRY_ATTR, countryIsoCode);

        final AddressForm addressForm = new AddressForm();
        model.addAttribute(ADDRESS_FORM_ATTR, addressForm);
        for (final AddressData addressData : userFacade.getAddressBook())
        {
            if (addressData.getId() != null && addressData.getId().equals(addressCode)
                    && countryIsoCode.equals(addressData.getCountry().getIsocode()))
            {
                model.addAttribute(ADDRESS_DATA_ATTR, addressData);
                addressDataUtil.convert(addressData, addressForm);
                break;
            }
        }
        return AdyenControllerConstants.Views.Pages.MultiStepCheckout.CountryAddressForm;
    }

    @ModelAttribute("titles")
    public Collection<TitleData> getTitles()
    {
        return userFacade.getTitles();
    }

    @ModelAttribute("countries")
    public Collection<CountryData> getCountries()
    {
        return checkoutFacade.getCountries(CountryType.SHIPPING);
    }
}
