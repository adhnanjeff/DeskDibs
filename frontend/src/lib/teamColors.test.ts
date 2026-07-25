import { describe, expect, it } from 'vitest';
import { TEAM_TINT_COUNT, teamTint } from './teamColors';

describe('teamTint', () => {
  it('gives one team the same colour every time it is asked', () => {
    // Seats arrive in any order and re-arrive over the websocket; a team's block must not
    // change colour underneath the user because a different seat rendered first.
    expect(teamTint(4)).toBe(teamTint(4));
  });

  it('gives neighbouring teams different colours', () => {
    expect(teamTint(4)).not.toBe(teamTint(5));
  });

  it('falls back to the default hold colour when the map names no team', () => {
    expect(teamTint(null)).toBe(teamTint(undefined));
    expect(teamTint(null)).toBe(teamTint(0));
  });

  it('always returns a colour, however large or negative the id', () => {
    for (const id of [0, 1, TEAM_TINT_COUNT, TEAM_TINT_COUNT * 3 + 1, 99999, -1, -7]) {
      expect(teamTint(id)).toMatch(/^#[0-9a-f]{6}$/i);
    }
  });
});
