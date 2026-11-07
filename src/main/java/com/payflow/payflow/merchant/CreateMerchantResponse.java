package com.payflow.payflow.merchant;

public record CreateMerchantResponse(
        String merchantId,
        String name,
        String email,
        String webhookSecret,
        String apiKey) { }