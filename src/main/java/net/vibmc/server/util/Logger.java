package net.vibmc.server.util;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small synchronized console logger suitable for the server's worker threads. */
public final class Logger {
    private final String name;
    private final boolean debugEnabled;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    public Logger(String name) {
        this(name, false);
    }

    public Logger(String name, boolean debugEnabled) {
        this.name = name;
        this.debugEnabled = debugEnabled;
    }

    public void info(String format, Object... args) {
        log("INFO", System.out, format, args);
    }

    public void warn(String format, Object... args) {
        log("WARN", System.err, format, args);
    }

    public void severe(String format, Object... args) {
        log("ERROR", System.err, format, args);
    }

    public void debug(String format, Object... args) {
        if (debugEnabled) {
            log("DEBUG", System.out, format, args);
        }
    }

    private synchronized void log(String level, PrintStream out, String format, Object... args) {
        String message;
        try {
            message = String.format(Locale.ROOT, format, args);
        } catch (RuntimeException ignored) {
            message = format;
        }
        out.println("[" + timestampFormat.format(new Date()) + "] [" + level + "] [" + name + "] " + message);
        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            ((Throwable) args[args.length - 1]).printStackTrace(out);
        }
    }
}
