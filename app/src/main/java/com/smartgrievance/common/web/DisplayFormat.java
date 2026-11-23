package com.smartgrievance.common.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component("fmt")
public class DisplayFormat {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a", Locale.ENGLISH);

    public String date(LocalDate value) { return value == null ? "" : DATE.format(value); }
    public String dateTime(LocalDateTime value) { return value == null ? "" : DATE_TIME.format(value); }
    public String days(Long value) { return value == null ? "" : value + (value == 1 ? " day" : " days"); }
}
