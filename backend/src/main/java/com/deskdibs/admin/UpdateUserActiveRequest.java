package com.deskdibs.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Activate or deactivate one account.
 *
 * <p>{@code Boolean} rather than {@code boolean} so that an omitted field fails validation instead
 * of silently defaulting to {@code false} — which, on this particular endpoint, would turn a
 * malformed request into a deactivation.
 */
public record UpdateUserActiveRequest(@NotNull Boolean active) {
}
