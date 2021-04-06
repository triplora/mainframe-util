package com.google.cloud.imf.util

import java.util.concurrent.TimeUnit

import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.Parameter.param
import org.mockserver.verify.VerificationTimes.exactly
import org.mockserver.matchers.Times
import org.mockserver.model.HttpError.error

import scala.util.Try

class BigQuerySpec extends MockedServerSpec {

  val bgHost = s"http://$localHost:$localPort"
  val bqService = Services.bigQuerySpec("projectA", "US", Services.bigqueryCredentials(), bgHost)

  //"GET https://www.googleapis.com/bigquery/v2/projects/{projectId}/datasets/{datasetId}/tables/{tableId}"
  val bqGetTableRequest = request()
    .withMethod("GET")
    .withPath("/bigquery/v2/projects/{projectId}/datasets/{datasetId}/tables/{tableId}")
    .withPathParameters(
      param("projectId", "[A-Z0-9\\-]+"),
      param("datasetId", "[A-Z0-9\\-]+"),
      param("tableId", "[A-Z0-9\\-]+"),
    )

  "Simulate delay on server side" should "perform retry BQ API calls when all calls failed"  in {
    //delay is longer then read timeout
    mockServer
      .when(bqGetTableRequest, Times.exactly(3))
      .respond(
        response()
          .withBody("very heavy computation operation on the server")
          .withDelay(TimeUnit.SECONDS, 21)
      )

    val resp = Try(bqService.getTable("datasetA", "tableA")).toEither

    mockServer.verify(bqGetTableRequest, exactly(3))
    assert(resp.isLeft)
  }

  "Simulate delay on server side" should "perform retry BQ API calls when last call successful"  in {
    //first 2 call delay is longer then read timeout
    mockServer
      .when(bqGetTableRequest, Times.exactly(2))
      .respond(
        response()
          .withBody("very heavy computation operation on the server")
          .withDelay(TimeUnit.SECONDS, 21)
      )

    //last call delay is lees then read timeout
    mockServer
      .when(bqGetTableRequest, Times.exactly(1))
      .respond(
        response()
          .withStatusCode(404)
          .withDelay(TimeUnit.SECONDS, 19)
      )

    val resp = Try(bqService.getTable("datasetA", "tableA")).toEither

    //Expected: 2 first calls fails due to timeout, last success, table not found
    mockServer.verify(bqGetTableRequest, exactly(3))
    assert(resp.isRight)
  }

  "Simulate connection error" should "perform retry API calls"  in {
    //delay is longer then read timeout
    mockServer
      .when(bqGetTableRequest, Times.exactly(4))
      .error(
        error()
          .withDropConnection(true)
          .withResponseBytes(Array.empty[Byte])
      )

    val resp = Try(bqService.getTable("datasetA", "tableA")).toEither

    mockServer.verify(bqGetTableRequest, exactly(4))
    assert(resp.isLeft)
  }
}
