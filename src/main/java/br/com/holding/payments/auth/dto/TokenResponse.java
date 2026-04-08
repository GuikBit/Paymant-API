package br.com.holding.payments.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String tokenType
) {
    public TokenResponse(String accessToken, String refreshToken, Long expiresIn) {
        this(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
