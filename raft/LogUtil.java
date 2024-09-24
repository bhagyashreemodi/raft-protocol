package raft;

import java.util.logging.*;

/**
 * Utility class for configuring and obtaining a {@link java.util.logging.Logger} instance with a custom format.
 * This class provides a static method to create a Logger with specific settings, including disabling parent handlers
 * to prevent global log settings from affecting the logger's output, and defining a custom log message format that
 * includes the timestamp, log level, log message, and the thread name for enhanced readability and debugging.
 */
public class LogUtil {

    /**
     * Creates or retrieves a logger with a custom format and configuration.
     * The logger is configured not to use global parent handlers, ensuring that
     * the log output is localized to the specific logger instance. It uses a
     * custom formatter to include the date, time, log level, message, and thread
     * name in each log entry.
     *
     * @param name The name of the logger. This name is usually the fully qualified
     *             class name of the object using the logger.
     * @return A {@link Logger} instance configured with a custom format and not using
     *         parent handlers.
     */
    public static Logger getLogger(String name) {
        Logger logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false); // Do not use global parent handlers

        // Define log format to include thread name
        SimpleFormatter formatter = new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%4$s] %3$s %n";

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                        System.currentTimeMillis(),
                        lr.getLevel().getLocalizedName(),
                        lr.getMessage(),
                        Thread.currentThread().getName() // Include thread name
                );
            }
        };

        // Set handler (where to log, and format)
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL); // Log all levels to console
        consoleHandler.setFormatter(formatter);
        logger.addHandler(consoleHandler);

        return logger;
    }
}
