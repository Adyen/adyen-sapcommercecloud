package com.adyen.commerce.connector.recurly.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.adyen.commerce.connector.recurly.RecurlyConnectionService;

@Controller
@RequestMapping("/test")
public class RecurlyTestController
{
    private final RecurlyConnectionService recurlyConnectionService;

    public RecurlyTestController(final RecurlyConnectionService recurlyConnectionService)
    {
        this.recurlyConnectionService = recurlyConnectionService;
    }

    @GetMapping("/connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection()
    {
        final Map<String, Object> response = new HashMap<>();

        try
        {
            final boolean accountCount =
                    recurlyConnectionService.testConnection();

            response.put("status", "OK");
            response.put("accountCount", accountCount);

            return ResponseEntity.ok(response);
        }
        catch (final Exception exception)
        {
            response.put("status", "ERROR");
            response.put("message", exception.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(response);
        }
    }

}
