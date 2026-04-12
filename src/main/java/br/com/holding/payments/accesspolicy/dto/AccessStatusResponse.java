package br.com.holding.payments.accesspolicy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AccessStatusResponse(
        Long customerId,
        String customerName,
        boolean allowed,
        List<String> reasons,
        String customBlockMessage,
        AccessSummary summary,
        LocalDateTime checkedAt
) {

    public record AccessSummary(
            long activeSubscriptions,
            long suspendedSubscriptions,
            long overdueCharges,
            BigDecimal totalOverdueValue,
            int oldestOverdueDays,
            BigDecimal creditBalance
    ) {}
}
