package br.com.holding.payments.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/asaas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Recebimento de webhooks do Asaas")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    @Operation(summary = "Receber webhook do Asaas",
            description = "Endpoint publico que recebe eventos do Asaas. Valida token, persiste payload bruto e retorna 200 imediatamente.")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "asaas-access-token", required = false) String accessToken,
            @RequestParam(value = "companyId") Long companyId,
            @RequestBody String rawPayload) {

        webhookService.receive(companyId, accessToken, rawPayload);
        return ResponseEntity.ok().build();
    }
}
