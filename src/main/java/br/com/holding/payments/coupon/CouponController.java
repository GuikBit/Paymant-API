package br.com.holding.payments.coupon;

import br.com.holding.payments.coupon.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Cupons", description = "Gerenciamento de cupons de desconto por empresa. " +
        "Suporta cupons para assinaturas (primeira cobranca ou recorrente) e cobrancas avulsas. " +
        "Desconto percentual (max 90%) ou valor fixo. Restricoes por plano, customer e ciclo.")
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @Operation(summary = "Criar cupom",
            description = "Cria um novo cupom de desconto. O campo 'code' e armazenado em UPPERCASE e deve ser unico por empresa. " +
                    "'scope' define se o cupom e para SUBSCRIPTION ou CHARGE. " +
                    "'discountValue' maximo de 90 para PERCENTAGE.")
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar cupons", description = "Retorna lista paginada de cupons da empresa.")
    public ResponseEntity<Page<CouponResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(couponService.findAll(pageable));
    }

    @GetMapping("/active")
    @Operation(summary = "Listar cupons vigentes", description = "Retorna apenas cupons ativos e dentro do periodo de validade.")
    public ResponseEntity<Page<CouponResponse>> findActive(Pageable pageable) {
        return ResponseEntity.ok(couponService.findActive(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cupom por ID")
    public ResponseEntity<CouponResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(couponService.findById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Buscar cupom por codigo")
    public ResponseEntity<CouponResponse> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(couponService.findByCode(code));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cupom",
            description = "Atualiza os dados do cupom. 'code' e 'scope' nao podem ser alterados.")
    public ResponseEntity<CouponResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateCouponRequest request) {
        return ResponseEntity.ok(couponService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar cupom (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        couponService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Reativar cupom")
    public ResponseEntity<CouponResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(couponService.activate(id));
    }

    @GetMapping("/{id}/usages")
    @Operation(summary = "Historico de utilizacoes", description = "Retorna lista paginada de utilizacoes do cupom.")
    public ResponseEntity<Page<CouponUsageResponse>> getUsages(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(couponService.getUsages(id, pageable));
    }
}
