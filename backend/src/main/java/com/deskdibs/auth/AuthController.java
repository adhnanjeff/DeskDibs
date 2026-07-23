package com.deskdibs.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/auth/me} — who the server thinks you are.
 *
 * <p>Present under both providers, and the same shape under both: that is the point of the
 * dual-provider seam. A client asks this once after sign-in and gets an identity and a role, with
 * no idea whether the token behind it was minted here or by Microsoft.
 *
 * <p>It is also the only place a client should learn its role from. The role in this response was
 * read from {@code app_user} while handling this request, so it reflects a demotion that happened
 * a minute ago — which a role baked into a token issued this morning would not.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUser currentUser;
    private final AuthProvider authProvider;

    public AuthController(CurrentUser currentUser, AuthProvider authProvider) {
        this.currentUser = currentUser;
        this.authProvider = authProvider;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return CurrentUserResponse.of(currentUser.require(), authProvider.kind());
    }
}
