package com.google.cloud.imf.util

import org.apache.log4j.{LogManager, Logger}

trait Logging {
  @transient
  protected implicit lazy val logger: Logger =
    LogManager.getLogger(this.getClass.getSimpleName.stripSuffix("$"))
}
