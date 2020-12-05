package com.google.cloud.imf.util

import java.io.{ByteArrayOutputStream, OutputStream, OutputStreamWriter, PrintStream, PrintWriter, StringWriter}
import java.nio.charset.{Charset, StandardCharsets}

import com.google.api.services.logging.v2.Logging
import com.google.api.services.logging.v2.model.{LogEntry, MonitoredResource, WriteLogEntriesRequest}
import com.google.auth.Credentials
import com.google.common.collect.ImmutableList
import org.apache.log4j.spi.LoggingEvent
import org.apache.log4j.{Appender, AppenderSkeleton, Layout, Level, LogManager, Logger, PatternLayout, WriterAppender}

import scala.collection.mutable

/** Provides methods to  */
object CloudLogging {
  private val cloudLoggingLoggers = mutable.Map.empty[String,CloudLogger]
  private var cloudLoggingLogInstance: Log = StdOutLog
  private var cloudLoggingStdOut: PrintStream = _
  private var cloudLoggingStdErr: PrintStream = _

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
    def setData(data: java.util.Map[String,Any]): Unit =
      if (data != null) mdc.putAll(data)

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

    def error(msg: String, data: java.util.Map[String,Any], e: Throwable): Unit = {
      val m = stringData(msg, data)
      if (e != null) {
        m.put("throwable", e.getClass.getCanonicalName.stripSuffix("$"))
        if (e.getMessage != null)
          m.put("message", e.getMessage)
        m.put("stackTrace", getStackTrace(e))
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
    private val mdc: java.util.Map[String,Any] = new java.util.HashMap[String,Any]()
    def putMdc(data: java.util.Map[String, Any]): Unit =
      if (data != null) mdc.putAll(data)

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
        data.putAll(mdc)
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

  class CloudLoggingAppender(private var log: Log) extends AppenderSkeleton {
    private val mdc: java.util.Map[String,Any] = new java.util.HashMap()
    def setLog(log: Log): Unit = this.log = log
    def setData(data: java.util.Map[String,Any]): Unit =
      if (data != null) mdc.putAll(data)

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

  def stdout(s: String): Unit = {
    Console.out.println(s)
    if (cloudLoggingStdOut != null) cloudLoggingStdOut.println(s)
  }

  def stderr(s: String): Unit = {
    Console.err.println(s)
    if (cloudLoggingStdErr != null) cloudLoggingStdErr.println(s)
  }

  /** Print to both stdout and stderr */
  def stdOutAndStdErr(s: String): Unit = {
    stdout(s)
    stderr(s)
  }

  def getStackTrace(e: Throwable): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    sw.toString
  }

  /** Prints optional user message and error message to stdout and stderr
   *  Prints stack trace to stderr
   */
  def printStackTrace(e: Throwable, message: String = ""): Unit = {
    val sb = new StringBuilder
    if (message.nonEmpty) {
      sb.append(message)
      sb.append(": \n")
    }
    if (e.getMessage != null)
      sb.append(e.getMessage)
    else
      sb.append(s"${e.getClass.getSimpleName.stripSuffix("$")} was thrown")
    stdOutAndStdErr(sb.result)
    stdout("See STDERR for stack trace")
    stderr(getStackTrace(e))
  }

  /** Initializes global Cloud Logging stdout and stderr */
  def configureStdout(logger:CloudLogger): Unit = {
    val outw: OutputStream =
      new BufferedCloudLoggerOutputStream("stdout", logger, Info, StandardCharsets.UTF_8)
    val errw: OutputStream =
      new BufferedCloudLoggerOutputStream("stderr", logger, Error, StandardCharsets.UTF_8)
    cloudLoggingStdOut = new PrintStream(outw, false, "UTF-8")
    cloudLoggingStdErr = new PrintStream(errw, false, "UTF-8")
    Runtime.getRuntime.addShutdownHook(new CloserThread(cloudLoggingStdOut, cloudLoggingStdErr))
  }

  val DefaultLayout = new PatternLayout("%d{ISO8601} %-5p %c %x - %m%n")

  def createAppender(out: OutputStream,
                     cs: Charset,
                     level: Level = Level.DEBUG,
                     layout: Layout = DefaultLayout): Appender = {
    val appender = new WriterAppender(layout, new OutputStreamWriter(out, cs))
    appender.setThreshold(level)
    appender
  }

  def createCloudAppender(log: Log, level: Level, mdc: java.util.Map[String,Any]): Appender = {
    val appender = new CloudLoggingAppender(log)
    appender.setData(mdc)
    appender.setThreshold(level)
    appender
  }

  /** Sets global Cloud Logging instance */
  def setCloudLoggingInstance(log: Log): Unit = {
    cloudLoggingLogInstance = log
    for ((_,logger) <- cloudLoggingLoggers) {
      logger.setLog(log)
    }
  }

  /** Sets MDC on global Cloud Logging instance */
  def setCloudLoggingMdc(mdc: java.util.Map[String,Any]): Unit = {
    cloudLoggingLogInstance match {
      case log: CloudLog => log.putMdc(mdc)
      case _ =>
    }
  }

  /** Initializes global Cloud Logging instance
   *  Adds CloudLoggingAppender to provided root logger
   */
  def configureCloudLogging(rootLogger: Logger,
                            projectId: String,
                            logId: String,
                            credentials: Credentials,
                            mdc: java.util.Map[String,Any]): Unit = {
    Console.out.println(s"Initializing Cloud Logging...")
    val cloudLog = new CloudLog(Services.logging(credentials), projectId, logId)
    setCloudLoggingMdc(mdc)
    setCloudLoggingInstance(cloudLog)

    val logger: CloudLogger = getLogger("com.google.cloud.imf")
    logger.setData(mdc)

    configureStdout(logger)

    // append individual error log messages to Cloud Logging
    rootLogger.addAppender(createCloudAppender(cloudLog, Level.ERROR, mdc))

    // append log messages to Cloud Logging stdout
    rootLogger.addAppender(createAppender(cloudLoggingStdOut, StandardCharsets.UTF_8))

    Console.out.println(s"Initialized Cloud Logging\nprojectId=$projectId\nlogId=$logId")
  }

  def configureLogging(debugOverride: Boolean = false,
                       env: Map[String,String] = sys.env,
                       errorLogs: Seq[String] = Seq.empty,
                       credentials: Credentials = null,
                       mdc: java.util.Map[String,Any] = null): Unit = {
    val logLevel =
      if (debugOverride || env.get("BQSH_ROOT_LOGGER").contains("DEBUG"))
        Level.DEBUG
      else Level.INFO
    val rootLogger = LogManager.getRootLogger
    rootLogger.removeAllAppenders()

    // append all log messages to stdout
    rootLogger.addAppender(createAppender(System.out, Charset.defaultCharset()))

    // append error log messages to stderr
    rootLogger.addAppender(createAppender(System.err, Charset.defaultCharset(), Level.ERROR))

    for {
      projectId <- env.get("LOG_PROJECT")
      logId <- env.get("LOG_ID")
      cred <- Option(credentials)
    } yield configureCloudLogging(rootLogger, projectId, logId, cred, mdc)

    // Quiet third-party logs
    for (logger <- errorLogs)
      LogManager.getLogger(logger).setLevel(Level.ERROR)

    // Enable debug logging if requested
    rootLogger.setLevel(logLevel)
  }

  def getLogger(loggerName: String): CloudLogger =
    cloudLoggingLoggers.getOrElseUpdate(loggerName, new CloudLogger(loggerName, cloudLoggingLogInstance))

  def getLogger(cls: Class[_]): CloudLogger =
    getLogger(cls.getSimpleName.stripSuffix("$"))
}
