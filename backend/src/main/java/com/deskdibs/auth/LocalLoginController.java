package com.deskdibs.auth;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/auth/login} — the development sign-in.
 *
 * <p>Exists only while {@code AUTH_PROVIDER=local}. Under Entra this bean is never created, so the
 * path is not merely disabled but absent: there is no controller to reach, whatever the filter
 * chain permits. Switching provider removes the password endpoint from the deployment rather than
 * leaving it there hoping nobody finds it.
 *
 * <p>Validate, delegate, map. The refusal path is not handled here at all — an
 * {@link InvalidCredentialsException} travels to {@link AuthExceptionHandler}, which is the single
 * place that decides what a refusal looks like on the wire.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "local")
public class LocalLoginController {

    private final LocalLoginService loginService;

    public LocalLoginController(LocalLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request.email(), request.password());
    }
}
