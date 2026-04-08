package br.com.holding.payments.company;

import br.com.holding.payments.company.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOLDING_ADMIN')")
@Tag(name = "Companies", description = "Company (tenant) management - holding admin only")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    @Operation(summary = "Register a new company")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(request));
    }

    @GetMapping
    @Operation(summary = "List all companies")
    public ResponseEntity<Page<CompanyResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(companyService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get company by ID")
    public ResponseEntity<CompanyResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update company")
    public ResponseEntity<CompanyResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    @PutMapping("/{id}/credentials")
    @Operation(summary = "Update Asaas API credentials")
    public ResponseEntity<Void> updateCredentials(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateCredentialsRequest request) {
        companyService.updateCredentials(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    @Operation(summary = "Test Asaas API connection with stored credentials")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = companyService.testConnection(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Connection successful" : "Connection failed"
        ));
    }
}
