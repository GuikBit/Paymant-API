package br.com.holding.payments.webhooksubscription;

import br.com.holding.payments.webhooksubscription.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhook-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN', 'COMPANY_OPERATOR')")
@Tag(name = "Webhook Subscriptions",
        description = "Cadastro de webhooks do tenant para receber eventos de dominio (cobrancas, assinaturas, planos).")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Criar webhook subscription",
            description = "Retorna o token bruto UMA UNICA VEZ em rawToken. Guarde com seguranca — apos essa resposta, so o token_prefix fica visivel.")
    public CreateWebhookSubscriptionResult create(@Valid @RequestBody CreateWebhookSubscriptionRequest request) {
        return service.create(request);
    }

    @GetMapping
    @Operation(summary = "Listar webhooks da empresa")
    public Page<WebhookSubscriptionResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes do webhook")
    public WebhookSubscriptionResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Atualizar webhook",
            description = "Campos nulos/vazios sao ignorados. Envie apenas o que quiser mudar.")
    public WebhookSubscriptionResponse update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateWebhookSubscriptionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Remover webhook",
            description = "Tambem apaga o historico de delivery attempts (ON DELETE CASCADE).")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/rotate-token")
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Rotacionar token",
            description = "Gera um novo token, invalida o anterior. Retorna o token bruto UMA UNICA VEZ.")
    public RotateTokenResult rotateToken(@PathVariable Long id) {
        return service.rotateToken(id);
    }

    @PostMapping("/{id}/ping")
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Enviar evento de teste (WebhookTestEvent)",
            description = "Cria uma delivery attempt com payload fake. O processamento acontece no ciclo do scheduler (proximos 5s).")
    public ResponseEntity<WebhookDeliveryAttemptResponse> ping(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.ping(id));
    }

    @GetMapping("/{id}/deliveries")
    @Operation(summary = "Historico de entregas do webhook")
    public Page<WebhookDeliveryAttemptResponse> listDeliveries(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listDeliveries(id, pageable);
    }

    @GetMapping("/event-types")
    @Operation(summary = "Catalogo de eventos suportados",
            description = "Lista os event types validos no campo eventTypes do cadastro. Use '*' para todos.")
    public List<WebhookEventCatalogResponse> listCatalog() {
        return service.listCatalog();
    }
}
