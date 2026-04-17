package br.com.holding.payments.dataimport.dto;

import java.util.Set;

public record WipeRequest(
        Set<WipeCategory> categories
) {}
