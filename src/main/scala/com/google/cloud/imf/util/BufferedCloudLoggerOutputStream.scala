package com.google.cloud.imf.util

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import com.google.cloud.imf.util.CloudLogging.{CloudLogger, Severity}

class BufferedCloudLoggerOutputStream(msgType: String,
                                      logger: CloudLogger,
                                      severity: Severity,
                                      size: Int = 200*1024) extends OutputStream {
  private val buf: ByteBuffer = ByteBuffer.allocate(size)
  private val data1: java.util.Map[String,Any] = {
    val m = new java.util.HashMap[String, Any]()
    m.put("msgType", msgType)
    m
  }

  private def ensureCapacity(n: Int): Unit = {
    if (buf.remaining() < n)
      flush()
  }

  override def write(b: Int): Unit = {
    ensureCapacity(1)
    buf.put(b.toByte)
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if (len <= buf.capacity()) {
      ensureCapacity(len)
      buf.put(b, off, len)
    }
  }

  override def flush(): Unit = {
    buf.flip()
    if (buf.hasRemaining) {
      logger.log(
        new String(buf.array(), 0, buf.remaining(), StandardCharsets.UTF_8), data1, severity)
    }
    buf.clear()
  }

  override def close(): Unit = flush()
}
