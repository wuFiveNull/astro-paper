package com.astropaper.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @GetMapping("/csrf")
    public CsrfTokenDto csrfToken(CsrfToken csrfToken) {
        return new CsrfTokenDto(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public UserSummaryDto login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        String attemptKey = loginRateLimiter.key(request.username(), servletRequest.getRemoteAddr());
        loginRateLimiter.assertAllowed(attemptKey);
        try {
            UserSummaryDto user = authService.login(request, servletRequest, servletResponse);
            loginRateLimiter.recordSuccess(attemptKey);
            return user;
        } catch (AuthenticationException exception) {
            loginRateLimiter.recordFailure(attemptKey);
            throw new InvalidCredentialsException();
        }
    }

    @GetMapping("/me")
    public UserSummaryDto currentUser(@AuthenticationPrincipal BlogUserPrincipal principal) {
        return authService.currentUser(principal);
    }
}
