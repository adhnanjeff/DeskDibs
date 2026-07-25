import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { SeatMapPage } from './SeatMapPage';
import { renderWithProviders } from '../test/renderWithProviders';

describe('SeatMapPage', () => {
  it('shows the table-shaped skeleton while loading, then the placeholder content', async () => {
    renderWithProviders(<SeatMapPage />);

    expect(screen.getByTestId('seatmap-skeleton')).toBeInTheDocument();

    expect(await screen.findByText(/placeholder view/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByTestId('seatmap-skeleton')).not.toBeInTheDocument();
    });

    // Real seat labels from the mocked seat map appear in the grouped list.
    expect(screen.getByText('L1')).toBeInTheDocument();
    expect(screen.getByText('Left Wing')).toBeInTheDocument();
    expect(screen.getByText('Right Wing')).toBeInTheDocument();
  });
});
