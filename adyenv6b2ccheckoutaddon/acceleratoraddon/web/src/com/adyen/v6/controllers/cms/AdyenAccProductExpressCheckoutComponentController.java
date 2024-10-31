package com.adyen.v6.controllers.cms;

import com.adyen.v6.model.contents.components.AdyenAccExpressCheckoutProductPageComponentModel;
import de.hybris.platform.addonsupport.controllers.cms.AbstractCMSAddOnComponentController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller(AdyenAccExpressCheckoutProductPageComponentModel._TYPECODE + "Controller")
@RequestMapping(value = "/view/" + AdyenAccExpressCheckoutProductPageComponentModel._TYPECODE + "Controller")
public class AdyenAccProductExpressCheckoutComponentController extends AbstractCMSAddOnComponentController<AdyenAccExpressCheckoutProductPageComponentModel> {

    @Override
    protected void fillModel(final HttpServletRequest request, final Model model, final AdyenAccExpressCheckoutProductPageComponentModel component) {
        model.addAttribute("test", "testValue");
    }
}
