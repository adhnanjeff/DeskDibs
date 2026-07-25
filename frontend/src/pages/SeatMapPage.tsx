import { useSeatMap } from '../hooks/useSeatMap';
import { useAuth } from '../auth/AuthContext';
import { SeatMapSkeleton } from '../components/seatmap/SeatMapSkeleton';
import { FloorWorkspace } from '../components/floormap/FloorWorkspace';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';

function SeatMapContent() {
  const { user } = useAuth();
  const { data, isPending, isError, error, refetch } = useSeatMap();

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

  return <FloorWorkspace seatMap={data} currentUserId={user?.id ?? null} />;
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
