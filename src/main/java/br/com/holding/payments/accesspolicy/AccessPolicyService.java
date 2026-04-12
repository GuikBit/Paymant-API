package br.com.holding.payments.accesspolicy;

import br.com.holding.payments.accesspolicy.dto.AccessPolicyResponse;
import br.com.holding.payments.accesspolicy.dto.UpdateAccessPolicyRequest;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessPolicyService {

    private final AccessPolicyRepository accessPolicyRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public AccessPolicyResponse getPolicy() {
        Long companyId = TenantContext.getRequiredCompanyId();
        AccessPolicy policy = getOrCreateDefault(companyId);
        return toResponse(policy);
    }

    @Transactional
    public AccessPolicyResponse updatePolicy(UpdateAccessPolicyRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        AccessPolicy policy = getOrCreateDefault(companyId);

        if (request.maxOverdueCharges() != null) policy.setMaxOverdueCharges(request.maxOverdueCharges());
        if (request.overdueToleranceDays() != null) policy.setOverdueToleranceDays(request.overdueToleranceDays());
        if (request.blockOnSuspendedSubscription() != null) policy.setBlockOnSuspendedSubscription(request.blockOnSuspendedSubscription());
        if (request.blockOnStandaloneCharges() != null) policy.setBlockOnStandaloneCharges(request.blockOnStandaloneCharges());
        if (request.blockOnSubscriptionCharges() != null) policy.setBlockOnSubscriptionCharges(request.blockOnSubscriptionCharges());
        if (request.blockOnNegativeCredit() != null) policy.setBlockOnNegativeCredit(request.blockOnNegativeCredit());
        if (request.customBlockMessage() != null) policy.setCustomBlockMessage(request.customBlockMessage().isBlank() ? null : request.customBlockMessage());
        if (request.cacheTtlMinutes() != null) policy.setCacheTtlMinutes(request.cacheTtlMinutes());

        policy = accessPolicyRepository.save(policy);
        log.info("Access policy atualizada para company_id={}", companyId);
        return toResponse(policy);
    }

    @Transactional
    public AccessPolicy getOrCreateDefault(Long companyId) {
        return accessPolicyRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    Company company = companyRepository.getReferenceById(companyId);
                    AccessPolicy policy = AccessPolicy.builder()
                            .company(company)
                            .build();
                    return accessPolicyRepository.save(policy);
                });
    }

    private AccessPolicyResponse toResponse(AccessPolicy p) {
        return new AccessPolicyResponse(
                p.getId(),
                p.getCompany().getId(),
                p.getMaxOverdueCharges(),
                p.getOverdueToleranceDays(),
                p.getBlockOnSuspendedSubscription(),
                p.getBlockOnStandaloneCharges(),
                p.getBlockOnSubscriptionCharges(),
                p.getBlockOnNegativeCredit(),
                p.getCustomBlockMessage(),
                p.getCacheTtlMinutes(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
