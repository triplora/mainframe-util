package com.google.cloud.imf.util

import java.io.PrintStream

class CloserThread(err: PrintStream, c: Seq[BufferedCloudLoggerOutputStream]) extends Thread {
  override def run(): Unit = {
    for (x <- c){
      try {
        x.flush()
      } catch {
        case t: Throwable =>
          t.printStackTrace(err)
      }
    }
  }
}
