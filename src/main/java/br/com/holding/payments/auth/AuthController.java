package br.com.holding.payments.auth;

import br.com.holding.payments.auth.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacao", description = "Endpoints de autenticacao, renovacao de token JWT e gerenciamento de usuarios do sistema")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Realizar login",
            description = "Autentica o usuario com email e senha, retornando um access token JWT e um refresh token. " +
                    "O access token deve ser enviado no header Authorization (Bearer) para acessar endpoints protegidos. " +
                    "O access token expira em 24 horas e o refresh token em 7 dias.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token",
            description = "Gera um novo par de access token e refresh token a partir de um refresh token valido. " +
                    "Utilize este endpoint quando o access token expirar, evitando que o usuario precise fazer login novamente. " +
                    "O refresh token anterior e invalidado apos o uso.")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Criar novo usuario",
            description = "Cria um novo usuario no sistema vinculado a uma empresa. " +
                    "Apenas administradores da holding (HOLDING_ADMIN) ou administradores da empresa (COMPANY_ADMIN) podem criar usuarios. " +
                    "O usuario criado recebe as roles especificadas e pode fazer login imediatamente.")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(request));
    }
}
