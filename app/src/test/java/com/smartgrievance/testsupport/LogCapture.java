package com.smartgrievance.testsupport;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

public final class LogCapture implements BeforeEachCallback, AfterEachCallback {
    private final List<Logger> loggers = List.of((Logger) LoggerFactory.getLogger("com.smartgrievance"),
            (Logger) LoggerFactory.getLogger("SECURITY"));
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @Override
    public void beforeEach(ExtensionContext context) {
        appender.list.clear();
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        loggers.forEach(logger -> logger.detachAppender(appender));
        appender.stop();
    }

    public List<ILoggingEvent> entries() { return List.copyOf(appender.list); }
}
