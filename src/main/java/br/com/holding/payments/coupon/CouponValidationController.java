package br.com.holding.payments.coupon;

import br.com.holding.payments.coupon.dto.CouponValidationResponse;
import br.com.holding.payments.coupon.dto.ValidateCouponRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons/validate")
@RequiredArgsConstructor
@Tag(name = "Validacao de Cupons", description = "Endpoints para validar cupons de desconto. " +
        "O endpoint publico executa validacoes basicas (sem checks por customer). " +
        "O endpoint autenticado executa todas as validacoes incluindo limites por customer e whitelist.")
public class CouponValidationController {

    private final CouponService couponService;

    @PostMapping("/public")
    @Operation(summary = "Validar cupom (publico)",
            description = "Validacao basica sem autenticacao. Verifica: existencia, vigencia, limite global, " +
                    "escopo, plano permitido e ciclo. Nao verifica limites por customer nem whitelist.")
    public ResponseEntity<CouponValidationResponse> validatePublic(@Valid @RequestBody ValidateCouponRequest request) {
        return ResponseEntity.ok(couponService.validatePublic(request));
    }

    @PostMapping
    @Operation(summary = "Validar cupom (autenticado)",
            description = "Validacao completa com todos os checks incluindo limite por customer e whitelist de customers.")
    public ResponseEntity<CouponValidationResponse> validateAuthenticated(@Valid @RequestBody ValidateCouponRequest request) {
        return ResponseEntity.ok(couponService.validateAuthenticated(request));
    }
}
