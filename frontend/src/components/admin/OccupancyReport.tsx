import { useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faDownload, faFileLines, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { DateField } from '../common/DateField';
import { useOccupancyReport, type DayOccupancyReport } from '../../hooks/useAdmin';

type Row = NonNullable<DayOccupancyReport['rows']>[number];

/** Office-local wall clock from an ISO timestamp — the arrival time, not the date. */
function timeOf(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Booked',
  CANCELLED: 'Cancelled',
  RELEASED_NO_SHOW: 'No-show',
};

/**
 * Who sat where on one day.
 *
 * <p>Deliberately includes the rows the floor map cannot show: a desk somebody booked and gave up,
 * and one the no-show release took back. The map answers "what can I book", so it only ever carries
 * live bookings for one date; this answers "what happened on the 4th", and a cancellation is part of
 * that answer.
 */
export function OccupancyReport() {
  const [date, setDate] = useState<string | null>(null);
  const report = useOccupancyReport(date);

  return (
    <section aria-label="Day occupancy report" className="flex flex-col gap-4">
      <div className="ui-edge border-line bg-paper p-5 shadow-[var(--dd-shadow)]">
        <p className="eyebrow text-xs text-ink/60">Day report</p>
        <p className="mt-1 max-w-2xl text-sm font-semibold text-ink/60">
          Pick a day to see who sat where, including desks that were cancelled or handed back by the
          no-show release.
        </p>
        <div className="mt-4 max-w-xs">
          <DateField label="Date" value={date ?? ''} onChange={setDate} />
        </div>
      </div>

      {report.isError && (
        <p role="alert" className="flex items-start gap-2 text-sm font-semibold text-danger">
          <FontAwesomeIcon icon={faTriangleExclamation} className="mt-0.5 h-4 w-4" aria-hidden="true" />
          {report.error instanceof Error ? report.error.message : 'Could not build that report.'}
        </p>
      )}

      {date == null && (
        <p className="flex items-center gap-2 text-sm font-semibold text-ink/50">
          <FontAwesomeIcon icon={faFileLines} className="h-4 w-4" aria-hidden="true" />
          Choose a date above to generate a report.
        </p>
      )}

      {report.isPending && date != null && (
        <p className="text-sm font-semibold text-ink/50">Building the report…</p>
      )}

      {report.data && <ReportBody report={report.data} />}
    </section>
  );
}

function ReportBody({ report }: { report: DayOccupancyReport }) {
  const rows = report.rows ?? [];
  const booked = report.bookedSeats ?? 0;
  const attended = report.attended ?? 0;
  const total = report.totalSeats ?? 0;

  return (
    <>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Stat label="Booked" value={booked} detail={`of ${total} desks`} />
        <Stat
          label="Turned up"
          value={attended}
          detail={booked > 0 ? `${Math.round((attended / booked) * 100)}% of bookings` : 'no bookings'}
        />
        <Stat label="No-shows" value={report.noShows ?? 0} detail="released at the cut-off" />
        <Stat label="Cancelled" value={report.cancelled ?? 0} detail="given up in advance" />
      </div>

      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-ink/60">
          {rows.length} {rows.length === 1 ? 'booking' : 'bookings'} on {report.date}
        </p>
        <button
          type="button"
          onClick={() => downloadCsv(report)}
          disabled={rows.length === 0}
          className="ui-control flex items-center gap-2 ui-edge border-line bg-paper px-3.5 py-2 text-sm font-bold ui-label text-ink shadow-[var(--dd-shadow-sm)] transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
        >
          <FontAwesomeIcon icon={faDownload} className="h-3.5 w-3.5" aria-hidden="true" />
          Download CSV
        </button>
      </div>

      {rows.length === 0 ? (
        <p className="ui-edge border-dashed border-line bg-paper-dim px-4 py-6 text-center text-sm font-semibold text-ink/50">
          Nobody booked a desk on {report.date}.
        </p>
      ) : (
        /* The table is wider than a phone. Scrolling it inside its own box keeps the page itself
           from scrolling sideways, which would move the whole layout to read one column. */
        <div className="overflow-x-auto ui-edge border-line bg-paper shadow-[var(--dd-shadow-sm)]">
          <table className="w-full min-w-[44rem] border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-line bg-paper-dim">
                <Th>Desk</Th>
                <Th>Person</Th>
                <Th>Team</Th>
                <Th>Status</Th>
                <Th>Checked in</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row: Row, index) => (
                <tr
                  key={`${row.seatLabel}-${row.userId}-${index}`}
                  className="border-b border-line last:border-b-0"
                >
                  <Td>
                    <span className="font-mono font-bold ui-label">{row.seatLabel}</span>
                  </Td>
                  <Td>
                    <span className="font-semibold text-ink">{row.userName}</span>
                    <span className="block text-xs text-ink-soft">{row.userEmail}</span>
                  </Td>
                  <Td>
                    <span className="text-ink-soft">
                      {(row.team ?? []).length > 0 ? (row.team ?? []).join(', ') : '—'}
                    </span>
                  </Td>
                  <Td>{STATUS_LABEL[row.status ?? ''] ?? row.status}</Td>
                  <Td>
                    <span className={row.checkedInAt ? 'text-ink' : 'text-ink-soft'}>
                      {timeOf(row.checkedInAt)}
                    </span>
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="px-3 py-2.5 text-xs font-bold ui-label text-ink/70">{children}</th>;
}

function Td({ children }: { children: React.ReactNode }) {
  return <td className="px-3 py-2.5 align-top">{children}</td>;
}

function Stat({ label, value, detail }: { label: string; value: number; detail: string }) {
  return (
    <div className="ui-edge border-line bg-paper px-3.5 py-3 shadow-[var(--dd-shadow-sm)]">
      <p className="eyebrow text-xs text-ink/60">{label}</p>
      <p className="font-mono text-3xl font-bold leading-tight text-ink">{value}</p>
      <p className="text-[13px] font-semibold ui-label text-ink-soft">{detail}</p>
    </div>
  );
}

/**
 * The report as a file, built in the browser from the rows already on screen.
 *
 * <p>No server round trip and no new endpoint: whatever is displayed is what gets exported, so the
 * two can never disagree. Fields are quoted and internal quotes doubled — a display name with a
 * comma in it would otherwise silently shift every later column by one.
 */
function downloadCsv(report: DayOccupancyReport) {
  const cell = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`;
  const lines = [
    ['Desk', 'Person', 'Email', 'Team', 'Status', 'Checked in'].map(cell).join(','),
    ...(report.rows ?? []).map((row) =>
      [
        row.seatLabel,
        row.userName,
        row.userEmail,
        (row.team ?? []).join(' / '),
        STATUS_LABEL[row.status ?? ''] ?? row.status,
        row.checkedInAt ?? '',
      ]
        .map(cell)
        .join(','),
    ),
  ];

  const url = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `deskdibs-${report.date}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
