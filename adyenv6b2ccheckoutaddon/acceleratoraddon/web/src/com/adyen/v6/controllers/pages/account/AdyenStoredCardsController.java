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
package com.adyen.v6.controllers.pages.account;

import com.adyen.v6.dto.*;
import com.adyen.commerce.facades.*;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.*;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.*;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.*;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.*;
import de.hybris.platform.cms2.exceptions.*;
import org.springframework.stereotype.*;
import org.springframework.ui.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.*;

import jakarta.annotation.*;

@Controller
@RequestMapping("/my-account/stored-cards")
public class AdyenStoredCardsController extends AbstractSearchPageController {

    private static final String REDIRECT_MY_ACCOUNT = REDIRECT_PREFIX + "/my-account";
    private static final String REDIRECT_MY_ACCOUNT_STOREDCARDS = REDIRECT_MY_ACCOUNT + "/stored-cards";
    private static final String STORED_CARDS_CMS_PAGE = "adyenStoredCards";

    @Resource(name = "accountBreadcrumbBuilder")
    private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

    @Resource(name = "adyenStoredCardsFacade")
    private AdyenStoredCardsFacade adyenStoredCardsFacade;

    @RequestMapping(method = RequestMethod.GET)
    @RequireHardLogIn
    public String listStoredCards(@Nonnull final Model model) throws CMSItemNotFoundException {
        final StoredCardsPageData pageData = adyenStoredCardsFacade.getStoredCardsPageDataForCurrentCustomer();

        storeCmsPageInModel(model, getContentPageForLabelOrId(STORED_CARDS_CMS_PAGE));
        setUpMetaDataForContentPage(model, getContentPageForLabelOrId(STORED_CARDS_CMS_PAGE));

        model.addAttribute("storedCards", pageData.getStoredCards());
        model.addAttribute("breadcrumbs", accountBreadcrumbBuilder.getBreadcrumbs("text.account.storedCards"));
        model.addAttribute("metaRobots", "no-index,no-follow");
        model.addAttribute("adyenClientKey", pageData.getClientKey());
        model.addAttribute("adyenCountryCode", pageData.getCountryCode());
        model.addAttribute("adyenEnvironment", pageData.getEnvironment());
        model.addAttribute("checkoutShopperHost", pageData.getCheckoutShopperHost());

        return getViewForPage(model);
    }

    @RequestMapping(value = "/remove", method = RequestMethod.POST)
    @RequireHardLogIn
    public String removeStoredCard(@RequestParam(value = "paymentInfoId") final String paymentInfoId,
                                   final RedirectAttributes redirectAttributes) {
        final boolean removed = adyenStoredCardsFacade.removeStoredCardForCurrentCustomer(paymentInfoId);

        if (removed) {
            GlobalMessages.addFlashMessage(
                    redirectAttributes,
                    GlobalMessages.CONF_MESSAGES_HOLDER,
                    "text.account.storedCard.delete.success"
            );
        } else {
            GlobalMessages.addFlashMessage(
                    redirectAttributes,
                    GlobalMessages.ERROR_MESSAGES_HOLDER,
                    "text.account.storedCard.delete.error"
            );
        }

        return REDIRECT_MY_ACCOUNT_STOREDCARDS;
    }
}
