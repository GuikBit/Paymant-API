package br.com.holding.payments.webhook;

import br.com.holding.payments.webhook.dto.CancelWebhookEventRequest;
import br.com.holding.payments.webhook.dto.WebhookEventResponse;
import br.com.holding.payments.webhook.dto.WebhookSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN', 'COMPANY_OPERATOR')")
@Tag(name = "Webhooks - Eventos", description = "Consulta de eventos webhook recebidos do Asaas. Os resultados sao automaticamente filtrados pela empresa do usuario autenticado via RLS.")
public class WebhookEventController {

    private final WebhookService webhookService;

    @GetMapping
    @Operation(summary = "Listar eventos webhook",
            description = "Lista os eventos webhook recebidos do Asaas para a empresa do usuario autenticado. " +
                    "Filtros opcionais por status e tipo de evento. Paginacao via query params (page, size).")
    public Page<WebhookEventResponse> list(
            @RequestParam(required = false) WebhookEventStatus status,
            @RequestParam(required = false) String eventType,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return webhookService.findAll(status, eventType, pageable);
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumo dos eventos webhook da empresa",
            description = "Retorna a contagem de eventos webhook agrupados por status para a empresa do usuario autenticado.")
    public WebhookSummaryResponse summary() {
        return webhookService.getSummary();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes de um evento webhook")
    public ResponseEntity<WebhookEventResponse> findOne(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.findOne(id));
    }

    @GetMapping(value = "/{id}/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Payload bruto do evento webhook",
            description = "Retorna o payload JSON bruto recebido do Asaas, util para debug e auditoria.")
    public ResponseEntity<String> payload(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.getPayload(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar evento webhook em retry",
            description = "Interrompe o loop de retry de um evento parado em DEFERRED, PENDING ou FAILED, movendo-o para DLQ. " +
                    "O evento mantem o historico e pode ser reprocessado depois via /admin/webhooks/{id}/replay. " +
                    "O corpo da requisicao e opcional; se enviado, o campo 'reason' sera registrado em last_error.")
    public ResponseEntity<WebhookEventResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelWebhookEventRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(webhookService.cancel(id, reason));
    }
}
