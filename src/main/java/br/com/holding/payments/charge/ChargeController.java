package br.com.holding.payments.charge;

import br.com.holding.payments.charge.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
@Tag(name = "Cobrancas", description = "Gerenciamento de cobrancas avulsas e parceladas. " +
        "Suporta PIX, boleto, cartao de credito e metodo indefinido (cliente escolhe). " +
        "Todas as cobrancas sao sincronizadas com o Asaas e possuem validacao de transicao de estado.")
public class ChargeController {

    private final ChargeService chargeService;

    @PostMapping("/pix")
    @Operation(summary = "Criar cobranca PIX",
            description = "Cria uma cobranca PIX no Asaas e persiste localmente. " +
                    "Apos a criacao, use GET /{id}/pix-qrcode para obter o QR Code e o codigo copia-e-cola. " +
                    "Aceita header Idempotency-Key para evitar cobrancas duplicadas.")
    public ResponseEntity<ChargeResponse> createPix(@Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createPixCharge(request));
    }

    @PostMapping("/boleto")
    @Operation(summary = "Criar cobranca por boleto",
            description = "Cria uma cobranca por boleto bancario no Asaas. " +
                    "O link do boleto e retornado no campo boletoUrl. " +
                    "Use GET /{id}/boleto-line para obter a linha digitavel.")
    public ResponseEntity<ChargeResponse> createBoleto(@Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createBoletoCharge(request));
    }

    @PostMapping("/credit-card")
    @Operation(summary = "Criar cobranca por cartao de credito",
            description = "Cria uma cobranca por cartao de credito no Asaas. " +
                    "Pode enviar dados do cartao (tokenizacao automatica) ou creditCardToken ja existente. " +
                    "O PAN do cartao NUNCA e persistido localmente — apenas o token do Asaas.")
    public ResponseEntity<ChargeResponse> createCreditCard(@Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createCreditCardCharge(request));
    }

    @PostMapping("/credit-card/installments")
    @Operation(summary = "Criar cobranca parcelada no cartao",
            description = "Cria um parcelamento no cartao de credito via Asaas. " +
                    "Informe installmentCount (minimo 2) e installmentValue (valor de cada parcela). " +
                    "O Asaas cria N cobrancas vinculadas ao parcelamento.")
    public ResponseEntity<ChargeResponse> createCreditCardInstallments(@Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createCreditCardInstallments(request));
    }

    @PostMapping("/boleto/installments")
    @Operation(summary = "Criar cobranca parcelada por boleto",
            description = "Cria um parcelamento por boleto bancario via Asaas. " +
                    "Informe installmentCount (minimo 2). Cada parcela gera um boleto individual.")
    public ResponseEntity<ChargeResponse> createBoletoInstallments(@Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createBoletoInstallments(request));
    }

    @PostMapping("/undefined")
    @Operation(summary = "Criar cobranca com metodo indefinido",
            description = "Cria uma cobranca sem definir o metodo de pagamento. " +
                    "O cliente escolhe o metodo (PIX, boleto ou cartao) no momento do pagamento via link do Asaas.")
    public ResponseEntity<ChargeResponse> createUndefined(@Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createUndefinedCharge(request));
    }

    @GetMapping
    @Operation(summary = "Listar cobrancas com filtros",
            description = "Retorna lista paginada de cobrancas com filtros opcionais: status, origin, " +
                    "periodo de vencimento (dueDateFrom/dueDateTo) e cliente (customerId). " +
                    "Exemplo: ?status=PENDING&origin=API&dueDateFrom=2024-01-01&page=0&size=20")
    public ResponseEntity<Page<ChargeResponse>> findAll(
            @RequestParam(required = false) ChargeStatus status,
            @RequestParam(required = false) ChargeOrigin origin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) Long customerId,
            Pageable pageable) {
        return ResponseEntity.ok(chargeService.findAll(status, origin, dueDateFrom, dueDateTo, customerId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cobranca por ID",
            description = "Retorna os dados completos de uma cobranca, incluindo status atual, " +
                    "URLs de pagamento (boleto, invoice) e dados de PIX (qrcode, copy-paste).")
    public ResponseEntity<ChargeResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.findById(id));
    }

    @GetMapping("/{id}/pix-qrcode")
    @Operation(summary = "Obter QR Code PIX",
            description = "Retorna o QR Code PIX (imagem base64) e o codigo copia-e-cola para uma cobranca PIX. " +
                    "Disponivel apenas para cobrancas do tipo PIX. O QR Code e cacheado localmente apos a primeira consulta.")
    public ResponseEntity<PixQrCodeResponse> getPixQrCode(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getPixQrCode(id));
    }

    @GetMapping("/{id}/boleto-line")
    @Operation(summary = "Obter linha digitavel do boleto",
            description = "Retorna a linha digitavel, nosso numero e codigo de barras do boleto. " +
                    "Disponivel apenas para cobrancas do tipo BOLETO.")
    public ResponseEntity<BoletoLineResponse> getBoletoLine(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getBoletoLine(id));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Estornar cobranca",
            description = "Realiza estorno total ou parcial da cobranca no Asaas. " +
                    "Se o campo 'value' nao for informado, estorna o valor total. " +
                    "Apenas cobrancas com status CONFIRMED ou RECEIVED podem ser estornadas.")
    public ResponseEntity<ChargeResponse> refund(@PathVariable Long id,
                                                  @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(chargeService.refund(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar cobranca",
            description = "Cancela a cobranca no Asaas e atualiza o status local para CANCELED. " +
                    "Apenas cobrancas com status PENDING, CONFIRMED ou OVERDUE podem ser canceladas. " +
                    "Publica evento ChargeCanceledEvent no outbox.")
    public ResponseEntity<ChargeResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.cancel(id));
    }

    @PostMapping("/{id}/regenerate-boleto")
    @Operation(summary = "Regenerar boleto",
            description = "Regenera a linha digitavel e URL do boleto para cobrancas PENDING ou OVERDUE. " +
                    "Util quando o boleto original expirou e o cliente precisa de um novo.")
    public ResponseEntity<ChargeResponse> regenerateBoleto(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.regenerateBoleto(id));
    }

    @PostMapping("/{id}/received-in-cash")
    @Operation(summary = "Marcar como recebido em dinheiro",
            description = "Marca a cobranca como paga em dinheiro (presencialmente). " +
                    "Transiciona o status para RECEIVED e publica evento ChargePaidEvent.")
    public ResponseEntity<ChargeResponse> markAsReceivedInCash(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.markAsReceivedInCash(id));
    }

    @PostMapping("/{id}/resend-notification")
    @Operation(summary = "Reenviar notificacao de cobranca",
            description = "Publica evento ChargeNotificationResendEvent no outbox para " +
                    "que o sistema externo (n8n) reenvie a notificacao ao cliente.")
    public ResponseEntity<ChargeResponse> resendNotification(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.resendNotification(id));
    }
}
