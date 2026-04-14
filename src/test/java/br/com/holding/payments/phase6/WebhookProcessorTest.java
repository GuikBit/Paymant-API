package br.com.holding.payments.phase6;

import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.webhook.*;
import br.com.holding.payments.webhook.WebhookEventHandler.HandleResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 6 - WebhookProcessor")
class WebhookProcessorTest {

    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private WebhookEventHandler webhookEventHandler;

    private MeterRegistry meterRegistry;
    private WebhookProcessor processor;
    private Company company;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        processor = new WebhookProcessor(webhookEventRepository, webhookEventHandler, meterRegistry);
        ReflectionTestUtils.setField(processor, "maxAttempts", 10);
        ReflectionTestUtils.setField(processor, "backoffMultiplier", 2);

        company = Company.builder().cnpj("11111111000111").razaoSocial("Test").build();
        company.setId(1L);
    }

    private WebhookEvent buildEvent(String eventType) {
        return WebhookEvent.builder()
                .id(1L)
                .company(company)
                .asaasEventId("evt_" + System.nanoTime())
                .eventType(eventType)
                .payload("{\"event\":\"" + eventType + "\",\"payment\":{\"id\":\"pay_123\"}}")
                .status(WebhookEventStatus.PENDING)
                .attemptCount(0)
                .receivedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Evento processado com sucesso marca como PROCESSED")
    void processEvents_success_shouldMarkProcessed() {
        WebhookEvent event = buildEvent("PAYMENT_RECEIVED");
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of(event));
        when(webhookEventHandler.handle(event)).thenReturn(HandleResult.processed("ok"));

        processor.processEvents();

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isNotNull();
        verify(webhookEventRepository).save(event);
    }

    @Test
    @DisplayName("Recurso nao encontrado -> DEFERRED com backoff")
    void processEvents_resourceNotFound_shouldDefer() {
        WebhookEvent event = buildEvent("PAYMENT_RECEIVED");
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of(event));
        when(webhookEventHandler.handle(event)).thenReturn(HandleResult.deferred("Resource not found"));

        processor.processEvents();

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.DEFERRED);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now().minusSeconds(1));
        assertThat(event.getLastError()).contains("Resource not found");
        verify(webhookEventRepository).save(event);
    }

    @Test
    @DisplayName("DEFERRED apos max tentativas -> DLQ")
    void processEvents_maxDeferrals_shouldMoveToDlq() {
        WebhookEvent event = buildEvent("PAYMENT_RECEIVED");
        event.setAttemptCount(10); // already at max
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of(event));
        when(webhookEventHandler.handle(event)).thenReturn(HandleResult.deferred("Resource not found"));

        processor.processEvents();

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.DLQ);
        assertThat(event.getLastError()).contains("Max attempts");
    }

    @Test
    @DisplayName("Transicao invalida -> FAILED (nao retenta automaticamente)")
    void processEvents_invalidTransition_shouldMarkFailed() {
        WebhookEvent event = buildEvent("PAYMENT_RECEIVED");
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of(event));
        when(webhookEventHandler.handle(event)).thenThrow(
                new IllegalStateTransitionException(1L, "CANCELED", "RECEIVED"));

        processor.processEvents();

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(event.getLastError()).contains("Invalid state transition");
        verify(webhookEventRepository).save(event);
    }

    @Test
    @DisplayName("Erro generico -> DEFERRED com retry")
    void processEvents_genericError_shouldDeferWithRetry() {
        WebhookEvent event = buildEvent("PAYMENT_RECEIVED");
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of(event));
        when(webhookEventHandler.handle(event)).thenThrow(new RuntimeException("Connection timeout"));

        processor.processEvents();

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.DEFERRED);
        assertThat(event.getLastError()).contains("Connection timeout");
        verify(webhookEventRepository).save(event);
    }

    @Test
    @DisplayName("Sem eventos pendentes nao faz nada")
    void processEvents_noEvents_shouldDoNothing() {
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of());

        processor.processEvents();

        verify(webhookEventHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Multiplos eventos processados em sequencia no mesmo batch")
    void processEvents_multipleBatch_shouldProcessAll() {
        WebhookEvent event1 = buildEvent("PAYMENT_RECEIVED");
        event1.setId(1L);
        WebhookEvent event2 = buildEvent("PAYMENT_CONFIRMED");
        event2.setId(2L);
        WebhookEvent event3 = buildEvent("PAYMENT_OVERDUE");
        event3.setId(3L);

        when(webhookEventRepository.findEventsToProcess(any(), anyInt()))
                .thenReturn(List.of(event1, event2, event3));
        when(webhookEventHandler.handle(any())).thenReturn(HandleResult.processed("ok"));

        processor.processEvents();

        assertThat(event1.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(event2.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(event3.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        verify(webhookEventRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("Timer de metricas eh registrado para cada evento processado")
    void processEvents_shouldRecordMetrics() {
        WebhookEvent event = buildEvent("PAYMENT_RECEIVED");
        when(webhookEventRepository.findEventsToProcess(any(), anyInt())).thenReturn(List.of(event));
        when(webhookEventHandler.handle(event)).thenReturn(HandleResult.processed("ok"));

        processor.processEvents();

        assertThat(meterRegistry.find("webhook_processing_duration_seconds").timer()).isNotNull();
        assertThat(meterRegistry.find("webhook_processing_duration_seconds").timer().count()).isEqualTo(1);
    }
}
