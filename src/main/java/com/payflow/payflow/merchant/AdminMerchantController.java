package com.payflow.payflow.merchant;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/admin/merchants")
public class AdminMerchantController {

    private final MerchantService service;
    private final String adminToken;

    public AdminMerchantController(MerchantService service,
                                   @Value("${payflow.security.admin-token}") String adminToken) {
        this.service = service;
        this.adminToken = adminToken;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMerchantResponse create(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody CreateMerchantRequest request) {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
        return service.createMerchant(request);
    }
}
