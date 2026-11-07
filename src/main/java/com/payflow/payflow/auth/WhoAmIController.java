package com.payflow.payflow.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WhoAmIController {

    @GetMapping("/v1/whoami")
    public Map<String, String> whoAmI(
            @RequestAttribute(ApiKeyFilter.MERCHANT_ATTR) String merchantId) {
        return Map.of("merchantId", merchantId);
    }
}