package br.com.holding.payments.dataimport.dto;

import java.util.Map;
import java.util.Set;

public record WipeResult(
        Long companyId,
        int totalDeleted,
        Set<WipeCategory> requestedCategories,
        Set<WipeCategory> effectiveCategories,
        Map<String, Integer> deletedByTable
) {}
