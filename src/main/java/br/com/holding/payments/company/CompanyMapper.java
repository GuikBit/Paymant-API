package br.com.holding.payments.company;

import br.com.holding.payments.company.dto.CompanyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    @Mapping(target = "hasAsaasKey", expression = "java(company.getAsaasApiKeyEncrypted() != null && !company.getAsaasApiKeyEncrypted().isBlank())")
    CompanyResponse toResponse(Company company);
}
