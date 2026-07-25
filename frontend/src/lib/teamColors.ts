/**
 * A stable colour per team, so a block of desks held for one team reads as one block.
 *
 * All team holds share a state — the `faUsers` icon, the "Team hold" label, and the team's own
 * name on hover — and this only varies the *hue within* that state. So it never carries meaning
 * on its own: two teams' holds are still both obviously holds, they are just distinguishable
 * from each other at a glance, which is the whole point when three teams have blocked out
 * neighbouring pods.
 *
 * Deterministic from the team id rather than assigned in render order, so a team keeps its colour
 * as seats load, refresh, or arrive over the websocket.
 */

/**
 * Muted, equally-weighted tints. Deliberately all in the same tonal range: they must read as
 * variations of one state, not as a second palette competing with the seat-state colours, and the
 * ink glyph drawn on top has to stay legible on every one.
 */
const TEAM_TINTS = [
  '#b9a4e6', // lavender — the original team-hold colour, so single-team offices look unchanged
  '#8fc7d6', // slate blue
  '#e0a9a1', // clay
  '#a8c98f', // sage
  '#e3c37a', // wheat
  '#c9a2c4', // mauve
  '#93b8c9', // dusty blue
  '#d4b48c', // sand
] as const;

/** The tint for a team, or the default hold colour when the seat map names no team. */
export function teamTint(teamId: number | null | undefined): string {
  if (teamId == null) return TEAM_TINTS[0];
  // Non-negative modulo: ids are positive in practice, but a negative would otherwise index off
  // the front of the array and yield undefined.
  const index = ((teamId % TEAM_TINTS.length) + TEAM_TINTS.length) % TEAM_TINTS.length;
  return TEAM_TINTS[index];
}

export const TEAM_TINT_COUNT = TEAM_TINTS.length;
