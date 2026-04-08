package br.com.holding.payments.outbox;

import br.com.holding.payments.outbox.dto.OutboxEventResponse;
import br.com.holding.payments.outbox.dto.OutboxSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOLDING_ADMIN')")
@Tag(name = "Admin - Outbox", description = "Endpoints administrativos para inspecao e gerenciamento de eventos do outbox. " +
        "O outbox armazena eventos de dominio que precisam ser publicados para sistemas externos (n8n, BI, ERPs). " +
        "Apenas administradores da holding tem acesso.")
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    @GetMapping
    @Operation(summary = "Listar eventos do outbox por status",
            description = "Retorna uma lista paginada de eventos do outbox filtrados por status. " +
                    "Status disponiveis: PENDING (aguardando envio), PUBLISHED (enviado com sucesso), " +
                    "FAILED (falha no envio, sera retentado automaticamente) e DLQ (falha definitiva apos N tentativas, " +
                    "requer intervencao manual). Exemplo: ?status=FAILED&page=0&size=20")
    public ResponseEntity<Page<OutboxEventResponse>> listEvents(
            @RequestParam(defaultValue = "PENDING") OutboxStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(outboxAdminService.findByStatus(status, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Obter resumo do outbox",
            description = "Retorna um resumo consolidado do outbox com a contagem de eventos por status " +
                    "(pendentes, publicados, falhos e na DLQ) e o lag em segundos do evento pendente mais antigo. " +
                    "Util para monitoramento operacional e deteccao de problemas no envio de eventos.")
    public ResponseEntity<OutboxSummaryResponse> summary() {
        return ResponseEntity.ok(outboxAdminService.getSummary());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Reprocessar evento com falha",
            description = "Recoloca um evento com status FAILED ou DLQ de volta na fila de processamento (status PENDING). " +
                    "O contador de tentativas e zerado, permitindo que o relay tente publicar novamente. " +
                    "Utilize apos corrigir o problema que causou a falha original (ex: destino indisponivel, payload invalido).")
    public ResponseEntity<Void> retry(@PathVariable Long id) {
        outboxAdminService.retryEvent(id);
        return ResponseEntity.noContent().build();
    }
}
