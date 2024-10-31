package com.adyen.v6.controllers.cms;

import com.adyen.v6.model.contents.components.AdyenAccExpressCheckoutCartPageComponentModel;
import de.hybris.platform.addonsupport.controllers.cms.AbstractCMSAddOnComponentController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller(AdyenAccExpressCheckoutCartPageComponentModel._TYPECODE + "Controller")
@RequestMapping(value = "/view/" + AdyenAccExpressCheckoutCartPageComponentModel._TYPECODE + "Controller")
public class AdyenAccCartExpressCheckoutComponentController extends AbstractCMSAddOnComponentController<AdyenAccExpressCheckoutCartPageComponentModel> {

    @Override
    protected void fillModel(final HttpServletRequest request, final Model model, final AdyenAccExpressCheckoutCartPageComponentModel component) {
        model.addAttribute("test", "testValue");
    }
}
