import type { components } from '../../api/schema';

// The generated schema marks every CurrentUserResponse field optional
// (springdoc doesn't emit a `required` array for it). Fixtures always
// populate every field, so tests can rely on that with a locally-narrowed
// type instead of non-null-asserting at every call site.
type CurrentUser = Required<components['schemas']['CurrentUserResponse']>;
type SeatMapResponse = components['schemas']['SeatMapResponse'];
type DayAvailability = components['schemas']['DayAvailabilityView'];
type BookingResponse = components['schemas']['BookingResponse'];

export const EMPLOYEE_USER: CurrentUser = {
  id: 1,
  email: 'employee@deskdibs.local',
  displayName: 'Dev Employee',
  role: 'EMPLOYEE',
  provider: 'LOCAL',
};

export const MANAGER_USER: CurrentUser = {
  id: 2,
  email: 'manager@deskdibs.local',
  displayName: 'Dev Manager',
  role: 'MANAGER',
  provider: 'LOCAL',
};

export const EMPLOYEE_TOKEN = 'mock-employee-token';
export const MANAGER_TOKEN = 'mock-manager-token';

export const MOCK_SEATMAP: SeatMapResponse = {
  date: '2026-07-24',
  floors: [
    {
      floorId: 1,
      name: 'Floor 1',
      zones: [
        {
          zoneId: 1,
          name: 'Left Wing',
          tables: [
            {
              tableId: 1,
              label: 'L1',
              capacity: 6,
              posX: 0,
              posY: 0,
              rotation: 0,
              seats: [
                {
                  seatId: 101,
                  seatLabel: 'L1-A1',
                  side: 'A',
                  seatIndex: 1,
                  accessible: false,
                  state: 'AVAILABLE',
                },
                {
                  seatId: 102,
                  seatLabel: 'L1-A2',
                  side: 'A',
                  seatIndex: 2,
                  accessible: false,
                  state: 'OCCUPIED',
                  occupantUserId: 1,
                  occupantDisplayName: 'Dev Employee',
                  checkedIn: false,
                },
              ],
            },
          ],
        },
        {
          zoneId: 2,
          name: 'Right Wing',
          tables: [
            {
              tableId: 2,
              label: 'R1',
              capacity: 6,
              posX: 100,
              posY: 0,
              rotation: 0,
              seats: [
                {
                  seatId: 201,
                  seatLabel: 'R1-A1',
                  side: 'A',
                  seatIndex: 1,
                  accessible: false,
                  state: 'DISABLED',
                },
              ],
            },
          ],
        },
      ],
    },
  ],
};

export const MOCK_BOOKINGS: BookingResponse[] = [
  {
    id: 1,
    seatId: 102,
    seatLabel: 'L1-A2',
    userId: 1,
    userDisplayName: 'Dev Employee',
    bookingDate: '2026-07-24',
    status: 'ACTIVE',
  },
];

/**
 * The date strip's horizon. Deliberately small — three days is enough to prove the strip renders
 * a row, marks the day you already hold a desk, and shows a fill bar, without fourteen fixtures
 * to keep in step with MOCK_SEATMAP.
 */
export const MOCK_HORIZON: DayAvailability[] = [
  { date: '2026-07-24', bookableSeats: 102, bookedSeats: 2, yourSeatLabel: 'L1-A2', bookable: true },
  { date: '2026-07-25', bookableSeats: 102, bookedSeats: 51, yourSeatLabel: null, bookable: true },
  // A Saturday: present in the strip, but shut.
  { date: '2026-07-26', bookableSeats: 102, bookedSeats: 0, yourSeatLabel: null, bookable: false },
];
