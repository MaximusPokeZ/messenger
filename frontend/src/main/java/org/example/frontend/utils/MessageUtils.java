package org.example.frontend.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MessageUtils {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd:MM.yy");

  public static String formatTime(long time) {
    Instant instant = Instant.ofEpochMilli(time);
    LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

    LocalDate messageDate = localDateTime.toLocalDate();
    LocalDate today = LocalDate.now(ZoneId.systemDefault());

    if (messageDate.isBefore(today)) {
      return localDateTime.format(DATE_FORMATTER);
    } else {
      return localDateTime.format(TIME_FORMATTER);
    }
  }

  private MessageUtils() {}
}
