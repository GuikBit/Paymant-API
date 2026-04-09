package br.com.holding.payments.phase6;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.webhook.*;
import br.com.holding.payments.webhook.dto.WebhookEventResponse;
import br.com.holding.payments.webhook.dto.WebhookSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 6 - WebhookService")
class WebhookServiceTest {

    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private CompanyRepository companyRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WebhookService webhookService;

    private Company company;

    @BeforeEach
    void setup() {
        company = Company.builder()
                .cnpj("11111111000111")
                .razaoSocial("Test LTDA")
                .webhookToken("secret-token-123")
                .build();
        company.setId(1L);
    }

    @Nested
    @DisplayName("receive()")
    class Receive {

        @Test
        @DisplayName("Payload valido com token correto persiste evento")
        void validPayload_shouldPersist() {
            String payload = """
                    {"id":"evt_001","event":"PAYMENT_RECEIVED","payment":{"id":"pay_123","status":"RECEIVED"}}
                    """;
            when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
            when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            webhookService.receive(1L, "secret-token-123", payload);

            verify(webhookEventRepository).save(argThat(event ->
                    event.getAsaasEventId().equals("evt_001") &&
                    event.getEventType().equals("PAYMENT_RECEIVED") &&
                    event.getStatus() == WebhookEventStatus.PENDING
            ));
        }

        @Test
        @DisplayName("Evento duplicado (constraint violation) retorna sem erro")
        void duplicateEvent_shouldBeIgnored() {
            String payload = """
                    {"id":"evt_dup","event":"PAYMENT_RECEIVED","payment":{"id":"pay_123"}}
                    """;
            when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
            when(webhookEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("uq_webhook_event"));

            // Should NOT throw
            webhookService.receive(1L, "secret-token-123", payload);

            verify(webhookEventRepository).save(any());
        }

        @Test
        @DisplayName("Token invalido lanca BusinessException")
        void invalidToken_shouldThrow() {
            when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

            assertThatThrownBy(() ->
                    webhookService.receive(1L, "wrong-token", "{\"id\":\"evt_001\",\"event\":\"PAYMENT_RECEIVED\"}")
            ).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Token");
        }

        @Test
        @DisplayName("Company sem webhook token aceita qualquer request")
        void noTokenConfigured_shouldAcceptAll() {
            company.setWebhookToken(null);
            when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
            when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            webhookService.receive(1L, null, "{\"id\":\"evt_001\",\"event\":\"PAYMENT_RECEIVED\",\"payment\":{\"id\":\"pay_1\"}}");

            verify(webhookEventRepository).save(any());
        }

        @Test
        @DisplayName("Company inexistente lanca ResourceNotFoundException")
        void companyNotFound_shouldThrow() {
            when(companyRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    webhookService.receive(99L, "token", "{}")
            ).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("replay()")
    class Replay {

        @Test
        @DisplayName("Replay de evento FAILED marca como PENDING para reprocessamento")
        void replayFailed_shouldMarkPending() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(5L).company(company).asaasEventId("evt_replay")
                    .eventType("PAYMENT_RECEIVED").payload("{}")
                    .status(WebhookEventStatus.FAILED).attemptCount(3).build();

            when(webhookEventRepository.findById(5L)).thenReturn(Optional.of(event));
            when(webhookEventRepository.save(any())).thenReturn(event);

            WebhookEventResponse result = webhookService.replay(5L);

            assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PENDING);
            assertThat(event.getNextAttemptAt()).isNotNull();
            assertThat(result.id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Replay de evento DLQ marca como PENDING")
        void replayDlq_shouldMarkPending() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(6L).company(company).asaasEventId("evt_dlq")
                    .eventType("PAYMENT_OVERDUE").payload("{}")
                    .status(WebhookEventStatus.DLQ).attemptCount(10).build();

            when(webhookEventRepository.findById(6L)).thenReturn(Optional.of(event));
            when(webhookEventRepository.save(any())).thenReturn(event);

            webhookService.replay(6L);

            assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PENDING);
        }

        @Test
        @DisplayName("Replay de evento PROCESSED lanca BusinessException")
        void replayProcessed_shouldThrow() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(7L).company(company).asaasEventId("evt_done")
                    .eventType("PAYMENT_RECEIVED").payload("{}")
                    .status(WebhookEventStatus.PROCESSED).build();

            when(webhookEventRepository.findById(7L)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> webhookService.replay(7L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FAILED ou DLQ");
        }

        @Test
        @DisplayName("Replay de evento inexistente lanca ResourceNotFoundException")
        void replayNotFound_shouldThrow() {
            when(webhookEventRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> webhookService.replay(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getSummary()")
    class Summary {

        @Test
        @DisplayName("Resumo retorna contagens corretas por status")
        void summary_shouldReturnCorrectCounts() {
            when(webhookEventRepository.countByStatus(WebhookEventStatus.PENDING)).thenReturn(5L);
            when(webhookEventRepository.countByStatus(WebhookEventStatus.PROCESSING)).thenReturn(1L);
            when(webhookEventRepository.countByStatus(WebhookEventStatus.DEFERRED)).thenReturn(3L);
            when(webhookEventRepository.countByStatus(WebhookEventStatus.PROCESSED)).thenReturn(100L);
            when(webhookEventRepository.countByStatus(WebhookEventStatus.FAILED)).thenReturn(2L);
            when(webhookEventRepository.countByStatus(WebhookEventStatus.DLQ)).thenReturn(1L);

            WebhookSummaryResponse summary = webhookService.getSummary();

            assertThat(summary.pending()).isEqualTo(5);
            assertThat(summary.processing()).isEqualTo(1);
            assertThat(summary.deferred()).isEqualTo(3);
            assertThat(summary.processed()).isEqualTo(100);
            assertThat(summary.failed()).isEqualTo(2);
            assertThat(summary.dlq()).isEqualTo(1);
            assertThat(summary.total()).isEqualTo(112);
        }
    }

    @Nested
    @DisplayName("accelerateDeferredForAsaasId()")
    class Accelerate {

        @Test
        @DisplayName("Acelera eventos DEFERRED com asaasId correspondente")
        void accelerate_shouldUpdateDeferredEvents() {
            when(webhookEventRepository.markDeferredReadyByAsaasId("pay_123")).thenReturn(2);

            webhookService.accelerateDeferredForAsaasId("pay_123");

            verify(webhookEventRepository).markDeferredReadyByAsaasId("pay_123");
        }
    }
}
