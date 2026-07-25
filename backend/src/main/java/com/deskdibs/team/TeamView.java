package com.deskdibs.team;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A team the caller is entitled to hold seats for.
 *
 * <p>Deliberately thin. The reservation UI needs a name to show and an id to submit, and nothing
 * about the team's membership — so nothing about its membership crosses the boundary.
 */
public record TeamView(
        @Schema(example = "3") long id,
        @Schema(example = "Platform") String name,
        @Schema(description = "Display name of the team's manager, when one is set.") String managerName) {

    static TeamView of(Team team) {
        return new TeamView(
                team.getId(),
                team.getName(),
                team.getManager() == null ? null : team.getManager().getDisplayName());
    }
}
