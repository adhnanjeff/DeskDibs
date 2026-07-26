import { useState } from 'react';
import { useSeatMap } from '../hooks/useSeatMap';
import { useSeatMapHorizon } from '../hooks/useSeatMapHorizon';
import { useOfficeDateRollover } from '../hooks/useOfficeDateRollover';
import { useAuth } from '../auth/AuthContext';
import { SeatMapSkeleton } from '../components/seatmap/SeatMapSkeleton';
import { FloorWorkspace } from '../components/floormap/FloorWorkspace';
import { DateStrip } from '../components/floormap/DateStrip';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';

function SeatMapContent() {
  const { user } = useAuth();

  // `null` means "whatever the office calls today" — the server decides, and only once the user
  // picks a different day does the client name a date at all.
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [socketLive, setSocketLive] = useState(true);

  const horizon = useSeatMapHorizon();
  const { data, isPending, isError, error, refetch } = useSeatMap(selectedDate ?? undefined, {
    live: socketLive,
  });

  useOfficeDateRollover(horizon.data?.[0]?.date);

  if (isPending) {
    return <SeatMapSkeleton />;
  }

  if (isError) {
    return (
      <ErrorFallback
        title="Couldn't load the seat map"
        message={error instanceof Error ? error.message : undefined}
        onRetry={() => void refetch()}
      />
    );
  }

  return (
    <>
      {horizon.data && (
        <DateStrip
          days={horizon.data}
          selectedDate={selectedDate ?? data.date ?? null}
          onSelect={(date) => setSelectedDate(date)}
        />
      )}
      <FloorWorkspace
        seatMap={data}
        currentUserId={user?.id ?? null}
        onLiveStatusChange={setSocketLive}
      />
    </>
  );
}

export function SeatMapPage() {
  return (
    <SectionErrorBoundary
      title="The seat map hit a snag"
      message="Something failed while rendering the floor. Your booking data is safe — try reloading this section."
    >
      <SeatMapContent />
    </SectionErrorBoundary>
  );
}
