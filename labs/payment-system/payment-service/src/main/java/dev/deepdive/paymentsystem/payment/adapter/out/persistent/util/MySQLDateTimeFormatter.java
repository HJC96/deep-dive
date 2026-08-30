package dev.deepdive.paymentsystem.payment.adapter.out.persistent.util;

import java.time.format.DateTimeFormatter;

public final class MySQLDateTimeFormatter {

    public static final DateTimeFormatter MYSQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MySQLDateTimeFormatter() {
    }
}
