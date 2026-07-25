package com.deskdibs.team;

/**
 * Object-level authorization refusal: this team is not this manager's to hold seats for.
 *
 * <p>The sibling of {@link ReservationAccessDeniedException}, for the other end of a hold's life.
 * {@code @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")} proves only that the caller is <em>a</em>
 * manager; it says nothing about <em>whose</em> manager. Without this check any manager could take
 * a block of desks in another department's name — a hold that the real manager of that team could
 * then release, but that nobody asked for.
 *
 * <p>Answered from the stored team-manager relationship and the acting user's stored role, never
 * from a role claim on the incoming token.
 */
public class TeamAccessDeniedException extends ReservationException {

    private final long teamId;
    private final long actingUserId;

    public TeamAccessDeniedException(long teamId, long actingUserId) {
        super("User " + actingUserId + " does not manage team " + teamId);
        this.teamId = teamId;
        this.actingUserId = actingUserId;
    }

    public long getTeamId() {
        return teamId;
    }

    public long getActingUserId() {
        return actingUserId;
    }

    @Override
    public ReservationErrorCode errorCode() {
        return ReservationErrorCode.TEAM_ACCESS_DENIED;
    }
}
