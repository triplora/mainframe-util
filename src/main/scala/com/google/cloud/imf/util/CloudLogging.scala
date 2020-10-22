package com.google.cloud.imf.util

import java.io.{ByteArrayOutputStream, OutputStream, OutputStreamWriter, PrintStream, PrintWriter, StringWriter}
import java.nio.charset.{Charset, StandardCharsets}

import com.google.api.services.logging.v2.Logging
import com.google.api.services.logging.v2.model.{LogEntry, MonitoredResource, WriteLogEntriesRequest}
import com.google.auth.Credentials
import com.google.common.collect.ImmutableList
import org.apache.log4j.spi.LoggingEvent
import org.apache.log4j.{AppenderSkeleton, Level, LogManager, PatternLayout, WriterAppender}

import scala.collection.mutable

/** Provides methods to  */
object CloudLogging {
  sealed trait Severity {def value: String}
  case object Debug extends Severity {override def value: String = "DEBUG"}
  case object Info extends Severity {override def value: String = "INFO"}
  case object Warning extends Severity {override def value: String = "WARNING"}
  case object Error extends Severity {override def value: String = "ERROR"}
  case object Critical extends Severity {override def value: String = "CRITICAL"}
  case object Off extends Severity {override def value: String = "OFF"}

  trait Log {
    def log(msg: String, severity: Severity): Unit
    def logJson(data: java.util.Map[String, Any], severity: Severity): Unit
  }

  object NoOpLog extends Log {
    override def log(msg: String, severity: Severity): Unit = {}
    override def logJson(data: java.util.Map[String, Any], severity: Severity): Unit = {}
  }

  object StdOutLog extends Log {
    override def log(msg: String, severity: Severity): Unit = System.out.println(msg)
    override def logJson(data: java.util.Map[String, Any], severity: Severity): Unit = {
      import scala.jdk.CollectionConverters.MapHasAsScala
      System.out.println(data.asScala.map(x => s"${x._1}=${x._2}").mkString(","))
    }
  }

  class CloudLogger(val loggerName: String, private var _log: Log) {
    def setLog(log: Log): Unit = _log = log

    private val mdc: java.util.Map[String,Any] = new java.util.HashMap[String,Any]()
    def setData(data: java.util.Map[String,Any]): Unit = mdc.putAll(data)

    private def stringData(msg: String, data: java.util.Map[String,Any]): java.util.Map[String,Any] = {
      val m = new java.util.HashMap[String,Any]()
      if (data != null) m.putAll(data)
      if (mdc != null) m.putAll(mdc)
      m.put("msg", msg)
      m.put("logger", loggerName)
      m
    }

    private def jsonData(entries: Iterable[(String,Any)], data: java.util.Map[String,Any]): java.util.Map[String,Any] = {
      val m = new java.util.HashMap[String,Any]()
      for ((k,v) <- entries) m.put(k,v)
      if (data != null) m.putAll(data)
      if (mdc != null) m.putAll(mdc)
      m.put("logger", loggerName)
      m
    }

    def log(msg: String, data: java.util.Map[String,Any], severity: Severity): Unit =
      _log.logJson(stringData(msg, data), severity)

    def logJson(entries: Iterable[(String,Any)], data: java.util.Map[String,Any], severity: Severity): Unit =
      _log.logJson(jsonData(entries, data), severity)

    def info(msg: String, data: java.util.Map[String,Any]): Unit =
      log(msg, data, Info)

    def infoJson(entries: Iterable[(String,Any)], data: java.util.Map[String,Any]): Unit =
      logJson(entries, data, Info)

    def error(msg: String, data: java.util.Map[String,Any], throwable: Throwable): Unit = {
      val m = stringData(msg, data)
      if (throwable != null) {
        val w = new StringWriter()
        throwable.printStackTrace(new PrintWriter(w))
        m.put("throwable", throwable.getClass.getCanonicalName.stripSuffix("$"))
        m.put("stackTrace", w.toString)
      }
      _log.logJson(m, CloudLogging.Error)
    }

    def log(msg: String, severity: Severity): Unit =
      log(msg, null, severity)

    def logJson(entries: Iterable[(String,Any)], severity: Severity): Unit =
      logJson(entries, null, severity)

    def error(msg: String, throwable: Throwable): Unit =
      error(msg, null, throwable)
  }

  private final val Global = new MonitoredResource().setType("global")

  class CloudLog(client: Logging, project: String, logId: String) extends Log {
    private val logName: String = s"projects/$project/logs/$logId"

    override def log(msg: String, severity: Severity): Unit = {
      if (client != null && severity != null && severity != Off) {
        val entry: LogEntry = new LogEntry()
          .setTextPayload(msg)
          .setSeverity(severity.value)
          .setLogName(logName)
          .setResource(Global)

        val req = new WriteLogEntriesRequest()
          .setLogName(logName)
          .setResource(Global)
          .setEntries(ImmutableList.of(entry))

        client.entries.write(req).execute
      }
    }

    override def logJson(data: java.util.Map[String, Any], severity: Severity): Unit = {
      if (client != null && data != null && severity != null && severity != Off) {
        val entry: LogEntry = new LogEntry()
          .setJsonPayload(data.asInstanceOf[java.util.Map[String,Object]])
          .setSeverity(severity.value)
          .setLogName(logName)
          .setResource(Global)
        val req: WriteLogEntriesRequest = new WriteLogEntriesRequest()
          .setLogName(logName)
          .setResource(Global)
          .setEntries(ImmutableList.of(entry))
        client.entries.write(req).execute
      }
    }
  }

  class ByteArrayWriter(val os: ByteArrayOutputStream = new ByteArrayOutputStream())
    extends PrintStream(os) {
    def result: String = new String(os.toByteArray, StandardCharsets.UTF_8)
  }

  class StackDriverLoggingAppender(private var log: Log) extends AppenderSkeleton {
    private var mdc: java.util.Map[String,Any] = _
    def setLog(log: Log): Unit = this.log = log
    def setData(data: java.util.Map[String,Any]): Unit = {
      if (mdc == null)
        mdc = new java.util.HashMap()
      mdc.putAll(data)
    }

    private def toMap(e: LoggingEvent): java.util.Map[String,Any] = {
      val m = new java.util.HashMap[String,Any]
      m.put("logger",e.getLoggerName)
      m.put("thread",e.getThreadName)
      e.getMessage match {
        case s: String =>
          m.put("msg",s)
        case (k: String, v: String) =>
          m.put(k,v)
        case x: java.util.Map[_,_] =>
          x.forEach{
            case (k: String, v: Any) =>
              m.put(k,v)
            case _ =>
          }
        case x: Iterable[_] =>
          for (entry <- x) {
            entry match {
              case (k: String, v: Any) =>
                m.put(k,v)
              case _ =>
            }
          }
        case _ =>
          m.put("msg",e.getRenderedMessage)
      }
      m.put("timestamp", e.getTimeStamp)
      if (e.getThrowableInformation != null){
        val w = new ByteArrayWriter
        e.getThrowableInformation
          .getThrowable.printStackTrace(w)
        m.put("stackTrace", w.result)
      }
      if (mdc != null)
        m.putAll(mdc)
      m
    }

    private def sev(l: Level): Severity = {
      import Level._
      l match {
        case INFO => Info
        case DEBUG => Debug
        case FATAL => Critical
        case TRACE => Debug
        case WARN => Warning
        case OFF => Off
        case _ => Info
      }
    }

    override def append(event: LoggingEvent): Unit =
      log.logJson(toMap(event), sev(event.getLevel))

    override def close(): Unit = {}

    override def requiresLayout(): Boolean = false
  }

  private val loggers = mutable.Map.empty[String,CloudLogger]
  private var instance: Log = StdOutLog
  private var clOut: PrintStream = _
  private var clErr: PrintStream = _

  def stdout(s: String): Unit = {
    if (clOut != null) clOut.println(s)
    System.out.println(s)
  }

  def stderr(s: String): Unit = {
    if (clErr != null) clErr.println(s)
    System.err.println(s)
  }

  def configureStdout(logger:CloudLogger): Unit = {
    val outw: OutputStream =
      new BufferedCloudLoggerOutputStream("stdout", logger, Info, StandardCharsets.UTF_8)
    val errw: OutputStream =
      new BufferedCloudLoggerOutputStream("stderr", logger, Error, StandardCharsets.UTF_8)
    clOut = new PrintStream(outw, false, "UTF-8")
    clErr = new PrintStream(errw, false, "UTF-8")
    Runtime.getRuntime.addShutdownHook(new CloserThread(clOut, clErr))
  }

  def configureLogging(debugOverride: Boolean = false,
                       env: Map[String,String] = sys.env,
                       errorLogs: Seq[String] = Seq.empty,
                       credentials: Credentials = null,
                       data: java.util.Map[String,Any] = null): Unit = {
    val debug = env.getOrElse("BQSH_ROOT_LOGGER","").contains("DEBUG") || debugOverride
    val rootLogger = LogManager.getRootLogger
    rootLogger.removeAllAppenders()
    val layout = new PatternLayout("%d{ISO8601} %-5p %c %x - %m%n")
    rootLogger.addAppender(new WriterAppender(
      layout, new OutputStreamWriter(System.out, Charset.defaultCharset())))

    if (credentials != null && env.contains("LOG_PROJECT") && env.contains("LOG_ID")) {
      val projectId = env("LOG_PROJECT")
      val logId = env("LOG_ID")
      System.out.println(s"Initializing Cloud Logging projectId=$projectId logId=$logId")
      instance = new CloudLog(Services.logging(credentials), projectId, logId)
      loggers.foreach(_._2.setLog(instance))
      val appender = new StackDriverLoggingAppender(instance)
      appender.setThreshold(org.apache.log4j.Level.ERROR)
      rootLogger.addAppender(appender)

      val logger: CloudLogger = getLogger("com.google.cloud.imf")
      if (data != null)
        logger.setData(data)

      configureStdout(logger)
      rootLogger.addAppender(new WriterAppender(layout,
        new OutputStreamWriter(clOut, StandardCharsets.UTF_8)))

      System.out.println("Finished initializing Cloud Logging.")
    }

    for (logger <- errorLogs)
      LogManager.getLogger(logger).setLevel(Level.ERROR)

    if (debug) {
      rootLogger.setLevel(Level.DEBUG)
    } else {
      rootLogger.setLevel(Level.INFO)
    }
  }

  def getLogger(loggerName: String): CloudLogger =
    loggers.getOrElseUpdate(loggerName, new CloudLogger(loggerName, instance))

  def getLogger(cls: Class[_]): CloudLogger =
    getLogger(cls.getSimpleName.stripSuffix("$"))
}
