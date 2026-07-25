import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SectionErrorBoundary } from './SectionErrorBoundary';

function Bomb(): never {
  throw new Error('Boom: render failure');
}

describe('SectionErrorBoundary', () => {
  it('catches a thrown render error and shows a fallback instead of a blank page', () => {
    // react-error-boundary re-throws to the console; silence the expected
    // noise so the test output stays readable.
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <SectionErrorBoundary title="Custom fallback title">
        <Bomb />
      </SectionErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Custom fallback title')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /try again/i }),
    ).toBeInTheDocument();

    consoleSpy.mockRestore();
  });
});
