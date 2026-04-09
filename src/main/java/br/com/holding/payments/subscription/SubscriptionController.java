package br.com.holding.payments.subscription;

import br.com.holding.payments.charge.dto.ChargeResponse;
import br.com.holding.payments.subscription.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Assinaturas", description = "Gerenciamento de assinaturas recorrentes")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar nova assinatura")
    public SubscriptionResponse create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return subscriptionService.subscribe(request);
    }

    @GetMapping
    @Operation(summary = "Listar assinaturas com filtros")
    public Page<SubscriptionResponse> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return subscriptionService.findAll(status, customerId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar assinatura por ID")
    public SubscriptionResponse findById(@PathVariable Long id) {
        return subscriptionService.findById(id);
    }

    @GetMapping("/{id}/charges")
    @Operation(summary = "Listar cobrancas da assinatura")
    public Page<ChargeResponse> listCharges(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return subscriptionService.listCharges(id, pageable);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar assinatura")
    public SubscriptionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return subscriptionService.update(id, request);
    }

    @PatchMapping("/{id}/payment-method")
    @Operation(summary = "Alterar metodo de pagamento")
    public SubscriptionResponse updatePaymentMethod(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentMethodRequest request) {
        return subscriptionService.updatePaymentMethod(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar assinatura")
    public SubscriptionResponse cancel(@PathVariable Long id) {
        return subscriptionService.cancel(id);
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pausar assinatura")
    public SubscriptionResponse pause(@PathVariable Long id) {
        return subscriptionService.pause(id);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Retomar assinatura pausada")
    public SubscriptionResponse resume(@PathVariable Long id) {
        return subscriptionService.resume(id);
    }
}
