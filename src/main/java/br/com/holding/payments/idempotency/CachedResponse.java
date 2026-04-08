package br.com.holding.payments.idempotency;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CachedResponse {
    private int status;
    private String body;
    private String requestHash;
}
