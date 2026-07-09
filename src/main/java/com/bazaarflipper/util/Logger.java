package com.bazaarflipper.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String PREFIX = "[BazaarFlipper] ";

    public static void info(String msg) {
        System.out.println(PREFIX + "[" + LocalTime.now().format(TIME_FMT) + "] [INFO] " + msg);
    }

    public static void warn(String msg) {
        System.out.println(PREFIX + "[" + LocalTime.now().format(TIME_FMT) + "] [WARN] " + msg);
    }

    public static void error(String msg) {
        System.err.println(PREFIX + "[" + LocalTime.now().format(TIME_FMT) + "] [ERROR] " + msg);
    }

    public static void error(String msg, Throwable t) {
        System.err.println(PREFIX + "[" + LocalTime.now().format(TIME_FMT) + "] [ERROR] " + msg);
        t.printStackTrace();
    }

    public static void debug(String msg) {
        // Could be gated by config
        System.out.println(PREFIX + "[" + LocalTime.now().format(TIME_FMT) + "] [DEBUG] " + msg);
    }
}
