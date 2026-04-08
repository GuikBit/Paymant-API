package br.com.holding.payments.customer;

import br.com.holding.payments.customer.dto.CustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCompany().getId(),
                customer.getAsaasId(),
                customer.getName(),
                customer.getDocument(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddressStreet(),
                customer.getAddressNumber(),
                customer.getAddressComplement(),
                customer.getAddressNeighborhood(),
                customer.getAddressCity(),
                customer.getAddressState(),
                customer.getAddressPostalCode(),
                customer.getCreditBalance(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
