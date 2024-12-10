package com.adyen.v6.controllers.cms;

import com.adyen.service.exception.ApiException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.model.contents.components.AdyenAccExpressCheckoutProductPageComponentModel;
import de.hybris.platform.acceleratorservices.data.RequestContextData;
import de.hybris.platform.addonsupport.controllers.cms.AbstractCMSAddOnComponentController;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Controller(AdyenAccExpressCheckoutProductPageComponentModel._TYPECODE + "Controller")
@RequestMapping(value = "/view/" + AdyenAccExpressCheckoutProductPageComponentModel._TYPECODE + "Controller")
public class AdyenAccProductExpressCheckoutComponentController extends AbstractCMSAddOnComponentController<AdyenAccExpressCheckoutProductPageComponentModel> {

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Autowired
    private ProductFacade productFacade;

    @Override
    protected void fillModel(final HttpServletRequest request, final Model model, final AdyenAccExpressCheckoutProductPageComponentModel component) {
        try {
            RequestContextData requestContextData = getRequestContextData(request);
            requestContextData.getProduct();
            final ProductData productData = productFacade.getProductForCodeAndOptions(requestContextData.getProduct().getCode(), Arrays.asList(ProductOption.BASIC, ProductOption.PRICE));

            adyenCheckoutFacade.initializeApplePayExpressPDPData(model, productData);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}
