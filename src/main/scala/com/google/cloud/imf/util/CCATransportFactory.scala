package com.google.cloud.imf.util

import java.util.concurrent.TimeUnit

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.apache.v2.ApacheHttpTransport
import com.google.auth.http.HttpTransportFactory
import com.google.common.collect.ImmutableList
import org.apache.http.client.HttpClient
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.{HttpClientBuilder, StandardHttpRequestRetryHandler}
import org.apache.http.message.BasicHeader

/** Creates HttpTransport with Apache HTTP */
object CCATransportFactory extends HttpTransportFactory with Logging {
  private var Instance: ApacheHttpTransport = _

  //Http client is shared between BqClient, BqStorageApi, GCStorage clients.
  //There are workloads, like Parallel Export, that will try to open one read and one write http connection per cpu.
  //Plus some logic could use additional connection for getting service information, like info about table or status of query execution.

  //connections_per_request = cpus_count * (1 read or write bq/gcs connection + 1 service connection)
  //connections_per_request = cpus_count * 2;
  //requests_count = cpus_count
  //maxConnectionTotal = requests_count * connections_per_request
  //maxConnectionTotal = 1 * connections_per_request
  private val connectionsPerRequest = 2 // 1 read or write bq/gcs connection + 1 service connection
  private val maxConnectionTotal = sys.env.get("HTTP_CLIENT_MAX_CONNECTIONS_COUNT").flatMap(_.toIntOption)
    .getOrElse(Runtime.getRuntime.availableProcessors() * connectionsPerRequest)

  override def create(): HttpTransport = CCATransportFactory.getTransportInstance

  def getTransportInstance: ApacheHttpTransport = {
    if (Instance == null) Instance = new ApacheHttpTransport(newDefaultHttpClient)
    Instance
  }

  def newDefaultHttpClient: HttpClient = {
    val socketConfig = SocketConfig.custom
      .setRcvBufSize(256 * 1024)
      .setSndBufSize(256 * 1024)
      .build

    logger.info(s"New http client was created with connection pool size $maxConnectionTotal")

    HttpClientBuilder.create
      .useSystemProperties
      .setSSLSocketFactory(CCASSLSocketFactory.getInstance)
      .setDefaultSocketConfig(socketConfig)
      .setMaxConnTotal(maxConnectionTotal)
      .setMaxConnPerRoute(maxConnectionTotal)
      .setConnectionTimeToLive(-1, TimeUnit.MILLISECONDS)
      .disableRedirectHandling
      .setRetryHandler(new StandardHttpRequestRetryHandler)
      .setDefaultHeaders(ImmutableList.of(new BasicHeader("user-agent", Services.UserAgent)))
      .build
  }
}
