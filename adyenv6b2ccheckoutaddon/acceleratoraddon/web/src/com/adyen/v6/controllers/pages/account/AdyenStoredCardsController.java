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

import com.adyen.model.recurring.RecurringDetail;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenCheckoutApiService;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Adyen stored cards
 */
@Controller
@RequestMapping("/my-account/stored-cards")
public class AdyenStoredCardsController extends AbstractSearchPageController {
    // Internal Redirects
    private static final String REDIRECT_MY_ACCOUNT = REDIRECT_PREFIX + "/my-account";
    private static final String REDIRECT_MY_ACCOUNT_STOREDCARDS = REDIRECT_MY_ACCOUNT + "/stored-cards";

    // CMS Pages
    private static final String STORED_CARDS_CMS_PAGE = "adyenStoredCards";

    private static final Logger LOGGER = Logger.getLogger(AdyenStoredCardsController.class);

    @Resource(name = "userService")
    protected UserService userService;

    @Resource(name = "accountBreadcrumbBuilder")
    private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

    @Resource(name = "baseStoreService")
    private BaseStoreService baseStoreService;

    @Resource(name = "adyenPaymentServiceFactory")
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;

    // handles the path as /my-account/stored-cards and displays the list of currently stored cards (recurring contracts)
    @RequestMapping(method = RequestMethod.GET)
    @RequireHardLogIn
    public String listStoredCards(@Nonnull final Model model) throws CMSItemNotFoundException {
        List<RecurringDetail> storedCards = getStoredCards();

        storeCmsPageInModel(model, getContentPageForLabelOrId(STORED_CARDS_CMS_PAGE));
        setUpMetaDataForContentPage(model, getContentPageForLabelOrId(STORED_CARDS_CMS_PAGE));
        model.addAttribute("storedCards", storedCards);
        model.addAttribute("breadcrumbs", accountBreadcrumbBuilder.getBreadcrumbs("text.account.storedCards"));
        model.addAttribute("metaRobots", "no-index,no-follow");

        return getViewForPage(model);
    }

    // Disables a recurring contract
    @RequestMapping(value = "/remove", method = RequestMethod.POST)
    @RequireHardLogIn
    public String removeStoredCard(@RequestParam(value = "paymentInfoId") final String paymentInfoId, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException {
        //First retrieve the list of stored cards for the given customer
        List<RecurringDetail> storedCards = getStoredCards();
        CustomerModel customer = getCurrentCustomer();

        if (paymentInfoId != null && ! paymentInfoId.isEmpty() && customer != null) {
            boolean contains = storedCards.stream().anyMatch(storedCard -> paymentInfoId.equals(storedCard.getRecurringDetailReference()));

            if (contains) {
                try {
                    getAdyenPaymentService().disableStoredCard(customer.getCustomerID(), paymentInfoId);

                    GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.CONF_MESSAGES_HOLDER, "text.account.storedCard.delete.success");

                    return REDIRECT_MY_ACCOUNT_STOREDCARDS;
                } catch (IOException e) {
                    LOGGER.error("IO Exception", e);
                } catch (ApiException e) {
                    LOGGER.error("Api Exception", e);
                }
            }
        }

        GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.ERROR_MESSAGES_HOLDER, "text.account.storedCard.delete.error");
        return REDIRECT_MY_ACCOUNT_STOREDCARDS;
    }

    private List<RecurringDetail> getStoredCards() {
        List<RecurringDetail> storedCards = new ArrayList<>();
        CustomerModel customer = getCurrentCustomer();

        if (customer != null) {
            try {
                storedCards = getAdyenPaymentService().getStoredCards(customer.getCustomerID());
            } catch (IOException e) {
                LOGGER.error("IOException", e);
            } catch (ApiException e) {
                LOGGER.error("ApiException", e);
            }
        }

        return storedCards;
    }

    private CustomerModel getCurrentCustomer() {
        if (! isAnonymousCheckout()) {
            return (CustomerModel) getUserService().getCurrentUser();
        }

        LOGGER.error("Customer not found");

        return null;
    }

    public AdyenCheckoutApiService getAdyenPaymentService() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        return adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStore);
    }

    public boolean isAnonymousCheckout() {
        return getUserService().isAnonymousUser(getUserService().getCurrentUser());
    }

    public UserService getUserService() {
        return userService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }
}
