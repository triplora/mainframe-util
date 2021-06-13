package com.google.cloud.imf.util

import com.google.api.services.logging.v2.Logging
import com.google.api.services.logging.v2.model.{LogEntry, MonitoredResource, WriteLogEntriesRequest}
import com.google.auth.Credentials

import java.util.Collections
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.log4j.{AppenderSkeleton, Level}
import org.apache.log4j.spi.LoggingEvent

class CloudLoggingAppender(projectId: String, logId: String, credentials: Credentials) extends AppenderSkeleton {
  private final val Global = new MonitoredResource().setType("global")
  private final val logName = s"projects/$projectId/logs/$logId"
  private final val logger: Logging = Services.logging(credentials)

  override def append(event: LoggingEvent): Unit = {
    //todo add event buffering here
    try {
      val entry: LogEntry = new LogEntry()
        .setJsonPayload(toMap(event))
        .setLogName(logName)
        .setResource(Global)
        .setSeverity(toSeverity(event.getLevel))

      val req = new WriteLogEntriesRequest()
        .setLogName(logName)
        .setResource(Global)
        .setEntries(Collections.singletonList(entry))

      logger.entries.write(req).execute
    } catch {
      case e: Exception =>
        System.out.println(s"Cloud logger failed to log. project=$projectId, logId=$logId, err=${e.getMessage}.")
    }
  }

  override def close(): Unit = {}

  override def requiresLayout(): Boolean = false

  private def toMap(e: LoggingEvent): java.util.Map[String, Object] = {
    val m = new java.util.HashMap[String, Any]
    m.put("logger", e.getLoggerName)
    m.put("thread", e.getThreadName)
    e.getMessage match {
      case s: String =>
        m.put("msg", s)
      case (k: String, v: String) =>
        m.put(k, v)
      case x: java.util.Map[_, _] =>
        x.forEach {
          case (k: String, v: Any) =>
            m.put(k, v)
          case _ =>
        }
      case x: Iterable[_] =>
        for (entry <- x) {
          entry match {
            case (k: String, v: Any) =>
              m.put(k, v)
            case _ =>
          }
        }
      case _ =>
        m.put("msg", e.getRenderedMessage)
    }
    m.put("timestamp", e.getTimeStamp)
    if (e.getThrowableInformation != null) {
      m.put("stackTrace", ExceptionUtils.getStackTrace(e.getThrowableInformation.getThrowable))
    }
    m.asInstanceOf[java.util.Map[String, Object]]
  }

  /**
   * @return severity values based on {@link com.google.logging.`type`.LogSeverity}
   */
  private def toSeverity(l: Level): String = {
    import Level._
    l match {
      case OFF => "OFF"
      case FATAL => "CRITICAL"
      case ERROR => "ERROR"
      case WARN => "WARNING"
      case INFO => "INFO"
      case DEBUG => "DEBUG"
      case TRACE => "DEBUG"
      case _ => "INFO"
    }
  }
}
