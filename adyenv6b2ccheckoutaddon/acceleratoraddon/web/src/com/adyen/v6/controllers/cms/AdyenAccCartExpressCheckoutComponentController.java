package com.adyen.v6.controllers.cms;

import com.adyen.service.exception.ApiException;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.v6.model.contents.components.AdyenAccExpressCheckoutCartPageComponentModel;
import de.hybris.platform.addonsupport.controllers.cms.AbstractCMSAddOnComponentController;
import de.hybris.platform.order.exceptions.CalculationException;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller(AdyenAccExpressCheckoutCartPageComponentModel._TYPECODE + "Controller")
@RequestMapping(value = "/view/" + AdyenAccExpressCheckoutCartPageComponentModel._TYPECODE + "Controller")
public class AdyenAccCartExpressCheckoutComponentController extends AbstractCMSAddOnComponentController<AdyenAccExpressCheckoutCartPageComponentModel> {

    @Resource
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Override
    protected void fillModel(final HttpServletRequest request, final Model model, final AdyenAccExpressCheckoutCartPageComponentModel component) {
        try {
            adyenCheckoutFacade.initializeExpressCheckoutCartPageData(model);
        } catch (ApiException | CalculationException e) {
            throw new RuntimeException(e);
        }
    }
}
