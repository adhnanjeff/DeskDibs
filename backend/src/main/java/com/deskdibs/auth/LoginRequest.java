package com.deskdibs.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Local sign-in credentials.
 *
 * <p>Validation here is purely about the shape of the input — it runs before any lookup, so a
 * rejection says nothing about whether the address belongs to anybody. The size bounds exist so an
 * enormous body cannot be pushed through BCrypt, which is intentionally slow.
 */
public record LoginRequest(

        @NotBlank @Email @Size(max = 320) String email,

        @NotBlank @Size(max = 200) String password) {
}
