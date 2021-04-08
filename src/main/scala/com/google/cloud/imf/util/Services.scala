package com.google.cloud.imf.util

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.retrying.RetrySettings
import com.google.api.gax.rpc.{FixedHeaderProvider, HeaderProvider}
import com.google.api.services.bigquery.BigqueryScopes
import com.google.api.services.logging.v2.{Logging, LoggingScopes}
import com.google.api.services.storage.StorageScopes
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.storage.v1.{BigQueryReadClient, BigQueryReadSettings}
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions}
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.{Storage, StorageOptions}
import org.slf4j.bridge.SLF4JBridgeHandler
import org.threeten.bp.Duration

import java.util.logging.{Level, Logger}

object Services {
  {
    //static block to initialize and configure redirect from java.util.logging to slf4j logger
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    Logger.getLogger("").setLevel(Level.ALL)
  }

  val UserAgent = "google-pso-tool/gszutil/5.0"
  val headerProvider: HeaderProvider = FixedHeaderProvider.create("user-agent", UserAgent)

  private def retrySettings(httpConfig: HttpConnectionConfigs): RetrySettings = RetrySettings.newBuilder
    .setMaxAttempts(httpConfig.maxRetryAttempts)
    .setTotalTimeout(Duration.ofMinutes(30))
    .setInitialRetryDelay(Duration.ofSeconds(10))
    .setMaxRetryDelay(Duration.ofMinutes(2))
    .setRetryDelayMultiplier(2.0d)
    .setMaxRpcTimeout(Duration.ofMinutes(2))
    .setRpcTimeoutMultiplier(2.0d)
    .build

  private def transportOptions(httpConfig: HttpConnectionConfigs): HttpTransportOptions = HttpTransportOptions.newBuilder
    .setHttpTransportFactory(CCATransportFactory)
    .setConnectTimeout(httpConfig.connectTimeoutInMillis)
    .setReadTimeout(httpConfig.readTimeoutInMillis)
    .build

  def storage(credentials: Credentials): Storage = {
    new StorageOptions.DefaultStorageFactory()
      .create(StorageOptions.newBuilder
        .setCredentials(credentials)
        .setTransportOptions(transportOptions(HttpConnectionConfigs.storageHttpConnectionConfigs))
        .setRetrySettings(retrySettings(HttpConnectionConfigs.storageHttpConnectionConfigs))
        .setHeaderProvider(headerProvider)
        .build)
  }

  def storageCredentials(): GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(StorageScopes.DEVSTORAGE_READ_WRITE)

  def storage(): Storage = storage(storageCredentials())

  /** Low-level GCS client */
  def storageApi(credentials: Credentials): com.google.api.services.storage.Storage = {
    new com.google.api.services.storage.Storage(CCATransportFactory.getTransportInstance,
      JacksonFactory.getDefaultInstance, new HttpCredentialsAdapter(credentials))
  }

  def bigqueryCredentials(): GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(BigqueryScopes.BIGQUERY)

  def bigQuery(project: String, location: String, credentials: Credentials): BigQuery =
    bigQueryOptions(project, location, credentials).getService

  /**
   * [DON'T USE IN PRODUCTION]
   * This service could be used in integration tests while mocking bigQuery client.
   *
   * @param project - bigQuery project
   * @param location - bigQuery location
   * @param credentials - bigQuery credentials
   * @param host - where BigQuery is running
   * @return BigQuery service which is running on provided host
   */
  def bigQuerySpec(project: String, location: String, credentials: Credentials, host: String): BigQuery =
    bigQueryOptions(project, location, credentials).toBuilder.setHost(host).build().getService

  private def bigQueryOptions(project: String, location: String, credentials: Credentials) =
    BigQueryOptions.newBuilder
      .setLocation(location)
      .setProjectId(project)
      .setCredentials(credentials)
      .setTransportOptions(transportOptions(HttpConnectionConfigs.bgHttpConnectionConfigs))
      .setRetrySettings(retrySettings(HttpConnectionConfigs.bgHttpConnectionConfigs))
      .setHeaderProvider(headerProvider)
      .build

  def bigQueryApi(credentials: Credentials): com.google.api.services.bigquery.Bigquery = {
    new com.google.api.services.bigquery.Bigquery.Builder(
      CCATransportFactory.getTransportInstance,
      JacksonFactory.getDefaultInstance,
      new HttpCredentialsAdapter(credentials))
      .setApplicationName(UserAgent).build()
  }

  def bigQueryStorage(credentials: Credentials): BigQueryReadClient = {
    BigQueryReadClient.create(BigQueryReadSettings
      .newBuilder()
      .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
      .setHeaderProvider(headerProvider)
      .setTransportChannelProvider(OkHttpTransportChannelProvider())
      .build())
  }

  def loggingCredentials(): GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(LoggingScopes.LOGGING_WRITE)

  def logging(credentials: Credentials): Logging =
    new Logging.Builder(CCATransportFactory.getTransportInstance,
      JacksonFactory.getDefaultInstance,
      new HttpCredentialsAdapter(credentials))
      .setApplicationName(UserAgent).build
}
