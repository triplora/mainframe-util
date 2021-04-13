package com.google.cloud.imf.util

import org.slf4j.bridge.SLF4JBridgeHandler

import java.util.logging.LogRecord

/**
 * Bridge only logs from com.google.api.gax.retrying logic to avoid infinite loop for usecase:
 * google-client(do oauth request) ->
 * http request info logged to JUL ->
 * JUL redirects all logs to Log4j ->
 * Log4j writes logs to all appender ->
 * CloudLogging appender tries to init itself and do auth ->
 * google-client(do oauth request) -> INFINITE LOOP HERE
 */
class SLF4JBridgeHandlerForGaxRetry extends SLF4JBridgeHandler{
  override def publish(record: LogRecord): Unit = {
    if (record.getLoggerName.startsWith("com.google.api.gax.retrying")) super.publish(record)
  }
}
