package com.adyen.v6.controllers.cms;

import com.adyen.service.exception.ApiException;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.v6.model.contents.components.AdyenAccExpressCheckoutProductPageComponentModel;
import de.hybris.platform.acceleratorservices.data.RequestContextData;
import de.hybris.platform.addonsupport.controllers.cms.AbstractCMSAddOnComponentController;
import de.hybris.platform.commercefacades.product.ProductFacade;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller(AdyenAccExpressCheckoutProductPageComponentModel._TYPECODE + "Controller")
@RequestMapping(value = "/view/" + AdyenAccExpressCheckoutProductPageComponentModel._TYPECODE + "Controller")
public class AdyenAccProductExpressCheckoutComponentController extends AbstractCMSAddOnComponentController<AdyenAccExpressCheckoutProductPageComponentModel> {

    @Resource
    private AdyenCheckoutFacade adyenCheckoutFacade;


    @Override
    protected void fillModel(final HttpServletRequest request, final Model model, final AdyenAccExpressCheckoutProductPageComponentModel component) {
        try {
            RequestContextData requestContextData = getRequestContextData(request);

            adyenCheckoutFacade.initializeExpressCheckoutPDPData(model, requestContextData.getProduct().getCode());
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}
