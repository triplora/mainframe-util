package com.google.cloud.imf.util

import scala.annotation.tailrec
import scala.util.Try

object RetryHelper extends Logging {

  def retryable[A](f: => A, logMessage: String = "", attempts: Int = 3, sleep: Int = 500, canRetry: Throwable => Boolean = _ => true): Either[Throwable, A] = {
    var count = 0

    @tailrec
    def call(): Either[Throwable, A] = {
      Try(f).toEither match {
        case Left(e) =>
          count += 1
          if (count <= attempts && canRetry(e)) {
            Try(Thread.sleep(sleep))
            logger.info(s"$logMessage. Failed to execute function, retry $count of $attempts", e)
            call()
          } else Left(e)
        case Right(value) => Right(value)
      }
    }

    call()
  }
}