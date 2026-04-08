package br.com.holding.payments.auth;

import br.com.holding.payments.auth.dto.*;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public TokenResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailAndActiveTrue(request.email())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new TokenResponse(accessToken, refreshToken, expirationMs);
    }

    public TokenResponse refresh(RefreshRequest request) {
        if (!jwtService.isTokenValid(request.refreshToken())) {
            throw new BusinessException("Invalid or expired refresh token");
        }

        String email = jwtService.getEmailFromToken(request.refreshToken());
        UserEntity user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new BusinessException("User not found or inactive"));

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new TokenResponse(accessToken, refreshToken, expirationMs);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already registered");
        }

        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.companyId()));

        UserEntity user = UserEntity.builder()
                .company(company)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .active(true)
                .build();
        user.setRoleSet(request.roles());

        user = userRepository.save(user);

        return new UserResponse(
                user.getId(),
                company.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoleSet(),
                user.getActive(),
                user.getCreatedAt()
        );
    }
}
