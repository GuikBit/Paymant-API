package br.com.holding.payments.charge.dto;

public record PixQrCodeResponse(
        String encodedImage,
        String copyPaste,
        String expirationDate
) {}
