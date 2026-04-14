package br.com.holding.payments.dataimport.dto;

import java.util.Map;

public record WipeResult(
        Long companyId,
        int totalDeleted,
        Map<String, Integer> deletedByTable
) {}
