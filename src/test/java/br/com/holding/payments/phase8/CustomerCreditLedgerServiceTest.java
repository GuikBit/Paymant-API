package br.com.holding.payments.phase8;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.creditledger.*;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 8 - CustomerCreditLedgerService")
class CustomerCreditLedgerServiceTest {

    @Mock
    private CustomerCreditLedgerRepository ledgerRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerCreditLedgerService service;

    private Customer customer;
    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder().build();
        company.setId(1L);
        customer = Customer.builder()
                .id(10L)
                .company(company)
                .name("Test Customer")
                .creditBalance(new BigDecimal("100.00"))
                .build();
    }

    // ==================== ADD CREDIT ====================

    @Nested
    @DisplayName("addCredit()")
    class AddCreditTests {

        @Test
        @DisplayName("Adiciona credito e atualiza saldo do customer")
        void addCredit_shouldIncrementBalanceAndSaveLedgerEntry() {
            when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customer));
            when(ledgerRepository.save(any())).thenAnswer(inv -> {
                CustomerCreditLedger entry = inv.getArgument(0);
                entry.setId(1L);
                return entry;
            });

            CustomerCreditLedger result = service.addCredit(
                    10L, new BigDecimal("50.00"),
                    CreditLedgerOrigin.DOWNGRADE_PRORATA, "ref-123",
                    "Credito pro-rata", "admin");

            assertThat(result.getType()).isEqualTo(CreditLedgerType.CREDIT);
            assertThat(result.getOrigin()).isEqualTo(CreditLedgerOrigin.DOWNGRADE_PRORATA);
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(result.getReferenceId()).isEqualTo("ref-123");
            assertThat(result.getDescription()).isEqualTo("Credito pro-rata");
            assertThat(result.getCreatedBy()).isEqualTo("admin");

            // Verifica que o saldo do customer foi atualizado
            assertThat(customer.getCreditBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
            verify(customerRepository).save(customer);
        }

        @Test
        @DisplayName("Adiciona credito com saldo inicial zero")
        void addCredit_fromZeroBalance_shouldWork() {
            customer.setCreditBalance(BigDecimal.ZERO);
            when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customer));
            when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCreditLedger result = service.addCredit(
                    10L, new BigDecimal("25.50"),
                    CreditLedgerOrigin.MANUAL_ADJUSTMENT, "ref-456",
                    "Ajuste manual", "admin");

            assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("25.50"));
            assertThat(customer.getCreditBalance()).isEqualByComparingTo(new BigDecimal("25.50"));
        }

        @Test
        @DisplayName("Customer nao encontrado lanca ResourceNotFoundException")
        void addCredit_customerNotFound_shouldThrow() {
            when(customerRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addCredit(
                    999L, new BigDecimal("50.00"),
                    CreditLedgerOrigin.DOWNGRADE_PRORATA, "ref", "desc", "admin"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== DEBIT CREDIT ====================

    @Nested
    @DisplayName("debitCredit()")
    class DebitCreditTests {

        @Test
        @DisplayName("Debita credito com saldo suficiente")
        void debitCredit_sufficientBalance_shouldDecrementBalance() {
            when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customer));
            when(ledgerRepository.save(any())).thenAnswer(inv -> {
                CustomerCreditLedger entry = inv.getArgument(0);
                entry.setId(2L);
                return entry;
            });

            CustomerCreditLedger result = service.debitCredit(
                    10L, new BigDecimal("30.00"),
                    CreditLedgerOrigin.CHARGE_APPLIED, "charge-789",
                    "Aplicado na cobranca", "system");

            assertThat(result.getType()).isEqualTo(CreditLedgerType.DEBIT);
            assertThat(result.getOrigin()).isEqualTo(CreditLedgerOrigin.CHARGE_APPLIED);
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("70.00"));

            assertThat(customer.getCreditBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
            verify(customerRepository).save(customer);
        }

        @Test
        @DisplayName("Debita saldo exato - zera o saldo")
        void debitCredit_exactBalance_shouldZeroOut() {
            when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customer));
            when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCreditLedger result = service.debitCredit(
                    10L, new BigDecimal("100.00"),
                    CreditLedgerOrigin.CHARGE_APPLIED, "charge-full",
                    "Saldo total aplicado", "system");

            assertThat(result.getBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(customer.getCreditBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Saldo insuficiente lanca BusinessException")
        void debitCredit_insufficientBalance_shouldThrow() {
            when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> service.debitCredit(
                    10L, new BigDecimal("150.00"),
                    CreditLedgerOrigin.CHARGE_APPLIED, "ref", "desc", "admin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("insuficiente")
                    .hasMessageContaining("100.00")
                    .hasMessageContaining("150.00");

            // Saldo nao deve ter sido alterado
            assertThat(customer.getCreditBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Saldo zero nao permite debito")
        void debitCredit_zeroBalance_shouldThrow() {
            customer.setCreditBalance(BigDecimal.ZERO);
            when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> service.debitCredit(
                    10L, new BigDecimal("1.00"),
                    CreditLedgerOrigin.CHARGE_APPLIED, "ref", "desc", "admin"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Customer nao encontrado lanca ResourceNotFoundException")
        void debitCredit_customerNotFound_shouldThrow() {
            when(customerRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.debitCredit(
                    999L, new BigDecimal("10.00"),
                    CreditLedgerOrigin.CHARGE_APPLIED, "ref", "desc", "admin"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GET BALANCE ====================

    @Nested
    @DisplayName("getBalance()")
    class GetBalanceTests {

        @Test
        @DisplayName("Retorna saldo do customer")
        void getBalance_shouldReturnCustomerCreditBalance() {
            when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

            BigDecimal balance = service.getBalance(10L);

            assertThat(balance).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Customer nao encontrado lanca ResourceNotFoundException")
        void getBalance_customerNotFound_shouldThrow() {
            when(customerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getBalance(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GET LEDGER ====================

    @Nested
    @DisplayName("getLedger()")
    class GetLedgerTests {

        @Test
        @DisplayName("Retorna pagina de entradas do ledger")
        void getLedger_shouldReturnPagedEntries() {
            Pageable pageable = PageRequest.of(0, 10);
            CustomerCreditLedger entry = CustomerCreditLedger.builder()
                    .id(1L).customer(customer).type(CreditLedgerType.CREDIT)
                    .amount(new BigDecimal("50.00")).balanceAfter(new BigDecimal("50.00"))
                    .build();
            Page<CustomerCreditLedger> page = new PageImpl<>(List.of(entry));

            when(ledgerRepository.findByCustomerIdOrderByCreatedAtDesc(10L, pageable)).thenReturn(page);

            Page<CustomerCreditLedger> result = service.getLedger(10L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        }
    }
}
