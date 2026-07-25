import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { SeatMapPage } from './SeatMapPage';
import { renderWithProviders } from '../test/renderWithProviders';

describe('SeatMapPage', () => {
  it('shows the table-shaped skeleton while loading, then the floor map', async () => {
    renderWithProviders(<SeatMapPage />);

    expect(screen.getByTestId('seatmap-skeleton')).toBeInTheDocument();

    // The floor's own name becomes the page heading once the map lands.
    expect(await screen.findByRole('heading', { name: /floor 1/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByTestId('seatmap-skeleton')).not.toBeInTheDocument();
    });

    // Each table from the mocked map renders as a labelled pod of seat tiles.
    expect(screen.getByText('L1')).toBeInTheDocument();
    expect(screen.getByText('R1')).toBeInTheDocument();
  });

  it('renders each seat with its state in the accessible name, not colour alone', async () => {
    renderWithProviders(<SeatMapPage />);

    // Available seats invite a click; occupied and disabled ones say why they don't.
    expect(
      await screen.findByRole('button', { name: /seat L1-A1: available/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /seat L1-A2: occupied — Dev Employee/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /seat R1-A1: disabled/i }),
    ).toBeInTheDocument();
  });
});
