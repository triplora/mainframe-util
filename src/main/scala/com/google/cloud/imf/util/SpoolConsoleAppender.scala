package com.google.cloud.imf.util

import org.apache.log4j.ConsoleAppender

import java.io.{OutputStream, OutputStreamWriter}
import java.nio.charset.Charset

class SpoolConsoleAppender extends ConsoleAppender {
  override def createWriter(os: OutputStream): OutputStreamWriter = {
    val wrapLogs = sys.env.get("LOG_WRAP_SPOOL")
      .flatMap(_.toBooleanOption).getOrElse(true)
    val logEncoding = Option(encoding).getOrElse(Charset.defaultCharset().name())
    super.createWriter(if (wrapLogs) new SpoolOutputStream(os, "\n".getBytes(logEncoding)) else os)
  }
}

class SpoolOutputStream(os: OutputStream, newLineBytes:Array[Byte]) extends OutputStream {
  override def write(i: Int): Unit = os.write(i)

  override def write(bytes: Array[Byte]): Unit = write(bytes, 0, bytes.length)

  override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
    var pos = off
    var remaining = math.min(len, bytes.length - off)
    var n = 0
    val limit = off + len
    var i = 0
    while (pos < limit && remaining > 0) {
      i = bytes.indexOfSlice(newLineBytes, pos)
      if (i < 0 || i - pos >= 80) {
        n = math.min(80, remaining)
        os.write(bytes, pos, n)
        os.write(newLineBytes)
      } else {
        n = math.min(i - pos + 1, remaining)
        os.write(bytes, pos, n)
      }
      remaining -= n
      pos += n
    }
  }

  override def flush(): Unit = os.flush()

  override def close(): Unit = os.close()
}