package com.deskdibs.booking;

import java.time.LocalDate;

/**
 * A desk was claimed for a day the office is shut.
 *
 * <p>Enforced here rather than only in the date strip: a greyed-out Saturday is a suggestion, and
 * the API has to be the thing that actually refuses. Carries the date and the day name so the
 * client can say which day it rejected instead of "invalid date".
 */
public class DateNotAWorkingDayException extends BookingException {

    private final LocalDate date;

    public DateNotAWorkingDayException(LocalDate date) {
        super("The office is closed on " + date.getDayOfWeek() + " (" + date + ")");
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDayOfWeek() {
        return date.getDayOfWeek().name();
    }

    @Override
    public BookingErrorCode errorCode() {
        return BookingErrorCode.DATE_NOT_A_WORKING_DAY;
    }
}
