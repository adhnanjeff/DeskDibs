package com.deskdibs.team;

/** No team with that id — refused rather than silently creating nothing to reserve for. */
public class TeamNotFoundException extends ReservationException {

    private final long teamId;

    public TeamNotFoundException(long teamId) {
        super("No team with id " + teamId);
        this.teamId = teamId;
    }

    public long getTeamId() {
        return teamId;
    }

    @Override
    public ReservationErrorCode errorCode() {
        return ReservationErrorCode.TEAM_NOT_FOUND;
    }
}
