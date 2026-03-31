package com.adyen.v6.controllers.cms;

import com.adyen.commerce.data.DataCollectionConfiguration;
import com.adyen.commerce.facades.AdyenDataCollectionFacade;
import com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.commerce.facades.impl.DefaultAdyenDataCollectionFacade;
import com.adyen.v6.model.contents.components.AdyenDataCollectionComponentModel;
import de.hybris.platform.addonsupport.controllers.cms.AbstractCMSAddOnComponentController;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller(AdyenDataCollectionComponentModel._TYPECODE + "Controller")
@RequestMapping(value = "/view/" + AdyenDataCollectionComponentModel._TYPECODE + "Controller")
public class AdyenDataCollectionComponentController extends AbstractCMSAddOnComponentController<AdyenDataCollectionComponentModel> {

    @Resource
    private AdyenDataCollectionFacade adyenDataCollectionFacade;

    @Override
    protected void fillModel(HttpServletRequest request, Model model, AdyenDataCollectionComponentModel component) {
        DataCollectionConfiguration dataCollectionConfiguration = adyenDataCollectionFacade.getDataCollectionConfiguration();

        model.addAttribute(DefaultAdyenCheckoutFacade.MODEL_CHECKOUT_SHOPPER_HOST, dataCollectionConfiguration.getCheckoutShopperHost());
        model.addAttribute(DefaultAdyenDataCollectionFacade.MODEL_DATA_CONFIGURATION_ENABLED, dataCollectionConfiguration.isDataCollectionEnabled());
    }
}
