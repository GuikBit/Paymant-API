package br.com.holding.payments.customer;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.customer.dto.*;
import br.com.holding.payments.integration.asaas.gateway.AsaasCustomerData;
import br.com.holding.payments.integration.asaas.gateway.AsaasCustomerResult;
import br.com.holding.payments.integration.asaas.gateway.AsaasGatewayService;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final CustomerMapper customerMapper;
    private final AsaasGatewayService asaasGateway;
    private final OutboxPublisher outboxPublisher;

    @Transactional
    @Auditable(action = "CUSTOMER_CREATE", entity = "Customer")
    public CustomerResponse create(CreateCustomerRequest request) {
        if (customerRepository.existsByDocument(request.document())) {
            throw new BusinessException("Documento ja cadastrado: " + request.document());
        }

        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        // Sync to Asaas
        AsaasCustomerResult asaasResult = asaasGateway.createCustomer(companyId, toAsaasData(request));

        Customer customer = Customer.builder()
                .company(company)
                .asaasId(asaasResult.asaasId())
                .name(request.name())
                .document(request.document())
                .email(request.email())
                .phone(request.phone())
                .addressStreet(request.addressStreet())
                .addressNumber(request.addressNumber())
                .addressComplement(request.addressComplement())
                .addressNeighborhood(request.addressNeighborhood())
                .addressCity(request.addressCity())
                .addressState(request.addressState())
                .addressPostalCode(request.addressPostalCode())
                .build();

        customer = customerRepository.save(customer);

        outboxPublisher.publish("CustomerCreatedEvent", "Customer",
                customer.getId().toString(), customerMapper.toResponse(customer));

        return customerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return customerRepository.search(search, pageable).map(customerMapper::toResponse);
        }
        return customerRepository.findAll(pageable).map(customerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return customerMapper.toResponse(getCustomerOrThrow(id));
    }

    @Transactional
    @Auditable(action = "CUSTOMER_UPDATE", entity = "Customer")
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = getCustomerOrThrow(id);

        if (request.name() != null) customer.setName(request.name());
        if (request.email() != null) customer.setEmail(request.email());
        if (request.phone() != null) customer.setPhone(request.phone());
        if (request.addressStreet() != null) customer.setAddressStreet(request.addressStreet());
        if (request.addressNumber() != null) customer.setAddressNumber(request.addressNumber());
        if (request.addressComplement() != null) customer.setAddressComplement(request.addressComplement());
        if (request.addressNeighborhood() != null) customer.setAddressNeighborhood(request.addressNeighborhood());
        if (request.addressCity() != null) customer.setAddressCity(request.addressCity());
        if (request.addressState() != null) customer.setAddressState(request.addressState());
        if (request.addressPostalCode() != null) customer.setAddressPostalCode(request.addressPostalCode());

        // Sync to Asaas
        if (customer.getAsaasId() != null) {
            Long companyId = TenantContext.getRequiredCompanyId();
            asaasGateway.updateCustomer(companyId, customer.getAsaasId(), toAsaasData(customer));
        }

        customer = customerRepository.save(customer);
        return customerMapper.toResponse(customer);
    }

    @Transactional
    @Auditable(action = "CUSTOMER_SOFT_DELETE", entity = "Customer")
    public void softDelete(Long id) {
        Customer customer = getCustomerOrThrow(id);
        customer.softDelete();
        customerRepository.save(customer);
        log.info("Customer soft-deleted: id={}", id);
    }

    @Transactional
    @Auditable(action = "CUSTOMER_RESTORE", entity = "Customer")
    public CustomerResponse restore(Long id) {
        Customer customer = customerRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        if (!customer.isDeleted()) {
            throw new BusinessException("Cliente nao esta deletado");
        }

        // Check if document is taken by another active customer
        String doc = customer.getDocument();
        Long customerId = customer.getId();
        customerRepository.findByDocument(doc)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(customerId)) {
                        throw new BusinessException("Documento ja em uso por outro cliente ativo: " + doc);
                    }
                });

        customer.restore();
        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse syncFromAsaas(Long id) {
        Customer customer = getCustomerOrThrow(id);
        if (customer.getAsaasId() == null) {
            throw new BusinessException("Cliente nao possui asaas_id para sincronizacao");
        }

        Long companyId = TenantContext.getRequiredCompanyId();
        AsaasCustomerResult asaasResult = asaasGateway.getCustomer(companyId, customer.getAsaasId());

        customer.setName(asaasResult.name());
        if (asaasResult.email() != null) customer.setEmail(asaasResult.email());
        if (asaasResult.phone() != null) customer.setPhone(asaasResult.phone());

        customer = customerRepository.save(customer);
        log.info("Customer synced from Asaas: id={}, asaasId={}", id, customer.getAsaasId());
        return customerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public Customer findOrCreate(String document) {
        return customerRepository.findByDocument(document).orElse(null);
    }

    private Customer getCustomerOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private AsaasCustomerData toAsaasData(CreateCustomerRequest request) {
        return new AsaasCustomerData(
                request.name(), request.document(), request.email(), request.phone(),
                null, request.addressStreet(), request.addressNumber(),
                request.addressComplement(), request.addressNeighborhood(),
                request.addressPostalCode(), null
        );
    }

    private AsaasCustomerData toAsaasData(Customer customer) {
        return new AsaasCustomerData(
                customer.getName(), customer.getDocument(), customer.getEmail(), customer.getPhone(),
                null, customer.getAddressStreet(), customer.getAddressNumber(),
                customer.getAddressComplement(), customer.getAddressNeighborhood(),
                customer.getAddressPostalCode(), null
        );
    }
}
