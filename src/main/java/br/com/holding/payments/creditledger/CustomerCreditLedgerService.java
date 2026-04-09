package br.com.holding.payments.creditledger;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerCreditLedgerService {

    private final CustomerCreditLedgerRepository ledgerRepository;
    private final CustomerRepository customerRepository;

    /**
     * Adds credit to a customer's balance (e.g., downgrade pro-rata).
     * Uses pessimistic lock on customer row to prevent race conditions.
     */
    @Transactional
    public CustomerCreditLedger addCredit(Long customerId, BigDecimal amount,
                                           CreditLedgerOrigin origin, String referenceId,
                                           String description, String createdBy) {
        Customer customer = lockCustomer(customerId);
        BigDecimal newBalance = customer.getCreditBalance().add(amount);
        customer.setCreditBalance(newBalance);
        customerRepository.save(customer);

        CustomerCreditLedger entry = CustomerCreditLedger.builder()
                .company(customer.getCompany())
                .customer(customer)
                .type(CreditLedgerType.CREDIT)
                .origin(origin)
                .referenceId(referenceId)
                .amount(amount)
                .balanceAfter(newBalance)
                .description(description)
                .createdBy(createdBy)
                .build();

        entry = ledgerRepository.save(entry);
        log.info("Credit added: customerId={}, amount={}, newBalance={}, origin={}",
                customerId, amount, newBalance, origin);
        return entry;
    }

    /**
     * Deducts credit from a customer's balance (e.g., applied to next charge).
     * Uses pessimistic lock on customer row to prevent race conditions.
     */
    @Transactional
    public CustomerCreditLedger debitCredit(Long customerId, BigDecimal amount,
                                             CreditLedgerOrigin origin, String referenceId,
                                             String description, String createdBy) {
        Customer customer = lockCustomer(customerId);
        if (customer.getCreditBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "Saldo de credito insuficiente. Disponivel: " + customer.getCreditBalance() + ", solicitado: " + amount);
        }

        BigDecimal newBalance = customer.getCreditBalance().subtract(amount);
        customer.setCreditBalance(newBalance);
        customerRepository.save(customer);

        CustomerCreditLedger entry = CustomerCreditLedger.builder()
                .company(customer.getCompany())
                .customer(customer)
                .type(CreditLedgerType.DEBIT)
                .origin(origin)
                .referenceId(referenceId)
                .amount(amount)
                .balanceAfter(newBalance)
                .description(description)
                .createdBy(createdBy)
                .build();

        entry = ledgerRepository.save(entry);
        log.info("Credit debited: customerId={}, amount={}, newBalance={}, origin={}",
                customerId, amount, newBalance, origin);
        return entry;
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        return customer.getCreditBalance();
    }

    @Transactional(readOnly = true)
    public Page<CustomerCreditLedger> getLedger(Long customerId, Pageable pageable) {
        return ledgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    private Customer lockCustomer(Long customerId) {
        return customerRepository.findByIdWithLock(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }
}
