package com.adyen.commerce.controllers.api;

import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.servicelayer.i18n.I18NService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping(value = "/api/checkout")
@Controller
public class AdyenTranslationsController {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private I18NService i18NService;

    @RequireHardLogIn
    @PostMapping(value = "/translations")
    public ResponseEntity<Map<String, String>> postWordsForTranslation(@RequestBody List<String> keys) {

        Map<String, String> translationMap = new HashMap<>();

        for (String key : keys) {
            String message = messageSource.getMessage(key, null, i18NService.getCurrentLocale());
            translationMap.put(key, message);
        }
        return ResponseEntity.ok().body(translationMap);
    }
}