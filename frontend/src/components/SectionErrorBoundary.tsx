import type { ReactNode } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { ErrorFallback } from './ErrorFallback';

interface SectionErrorBoundaryProps {
  children: ReactNode;
  title?: string;
  message?: string;
}

/**
 * A render failure in a page section (a bug, malformed data reaching a
 * component that assumes a shape) must never blank the whole page. This
 * wraps react-error-boundary with DeskDibs' shared fallback and a "Try
 * again" action that resets the boundary so a transient failure can
 * recover without a full page reload.
 */
export function SectionErrorBoundary({
  children,
  title,
  message,
}: SectionErrorBoundaryProps) {
  return (
    <ErrorBoundary
      FallbackComponent={({ resetErrorBoundary }) => (
        <ErrorFallback
          title={title}
          message={message}
          onRetry={resetErrorBoundary}
        />
      )}
    >
      {children}
    </ErrorBoundary>
  );
}
