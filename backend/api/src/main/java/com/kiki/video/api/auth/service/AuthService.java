package com.kiki.video.api.auth.service;

import com.kiki.video.api.auth.dto.LoginRequest;
import com.kiki.video.api.auth.dto.LoginResponse;
import com.kiki.video.api.auth.dto.RegisterRequest;
import com.kiki.video.api.auth.jwt.JwtService;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.user.dto.UserResponse;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.user.model.UserRole;
import com.kiki.video.api.user.model.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().toLowerCase(Locale.ROOT);
        String displayName = request.username();

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(displayName);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw translateDuplicate(ex);
        }

        return UserResponse.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        String identifier = request.identifier().toLowerCase(Locale.ROOT);
        User user = userMapper.findByUsernameOrEmail(identifier);
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    ErrorCode.INVALID_CREDENTIALS,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username/email or password"
            );
        }

        String accessToken = jwtService.createAccessToken(user);
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.expiresInSeconds(),
                UserResponse.from(user)
        );
    }

    private ApiException translateDuplicate(DuplicateKeyException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("users_email") || message.contains("(email)")) {
            return new ApiException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    HttpStatus.CONFLICT,
                    "Email is already in use"
            );
        }
        return new ApiException(
                ErrorCode.USERNAME_ALREADY_EXISTS,
                HttpStatus.CONFLICT,
                "Username is already in use"
        );
    }
}
