package br.com.holding.payments.webhook;

import br.com.holding.payments.webhook.dto.WebhookEventResponse;
import br.com.holding.payments.webhook.dto.WebhookSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'SYSTEM')")
@Tag(name = "Admin - Webhooks", description = "Administracao de eventos webhook")
public class WebhookAdminController {

    private final WebhookService webhookService;

    @GetMapping
    @Operation(summary = "Listar eventos webhook por status")
    public Page<WebhookEventResponse> listByStatus(
            @RequestParam WebhookEventStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return webhookService.findByStatus(status, pageable);
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumo dos eventos webhook")
    public WebhookSummaryResponse getSummary() {
        return webhookService.getSummary();
    }

    @PostMapping("/{eventId}/replay")
    @Operation(summary = "Reprocessar evento webhook",
            description = "Permite reprocessamento manual de eventos com status FAILED ou DLQ")
    public ResponseEntity<WebhookEventResponse> replay(@PathVariable Long eventId) {
        return ResponseEntity.ok(webhookService.replay(eventId));
    }
}
