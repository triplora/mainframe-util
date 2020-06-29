package com.google.cloud.imf.util

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.gax.retrying.RetrySettings
import com.google.api.gax.rpc.FixedHeaderProvider
import com.google.api.services.bigquery.BigqueryScopes
import com.google.api.services.logging.v2.{Logging, LoggingScopes}
import com.google.api.services.storage.StorageScopes
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions}
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.{Storage, StorageOptions}
import org.threeten.bp.Duration

object Services {
  private val retrySettings: RetrySettings = RetrySettings.newBuilder
    .setMaxAttempts(2)
    .setTotalTimeout(Duration.ofMinutes(30))
    .setInitialRetryDelay(Duration.ofSeconds(2))
    .setMaxRetryDelay(Duration.ofSeconds(8))
    .setRetryDelayMultiplier(2.0d)
    .build

  private val transportOptions: HttpTransportOptions = HttpTransportOptions.newBuilder
    .setHttpTransportFactory(CCATransportFactory)
    .build

  def storage(credentials: Credentials): Storage = {
    new StorageOptions.DefaultStorageFactory()
      .create(StorageOptions.newBuilder
        .setCredentials(credentials)
        .setTransportOptions(transportOptions)
        .setRetrySettings(retrySettings)
        .setHeaderProvider(FixedHeaderProvider.create("user-agent", "mainframe-util"))
        .build)
  }

  def storageCredentials(): GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(StorageScopes.DEVSTORAGE_READ_WRITE)

  def storage(): Storage = storage(storageCredentials())

  def bigqueryCredentials(): GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(BigqueryScopes.BIGQUERY)

  def bigQuery(project: String, location: String, credentials: Credentials): BigQuery = {
    BigQueryOptions.newBuilder
      .setLocation(location)
      .setProjectId(project)
      .setCredentials(credentials)
      .setTransportOptions(transportOptions)
      .setRetrySettings(retrySettings)
      .setHeaderProvider(FixedHeaderProvider.create("user-agent", "mainframe-util"))
      .build
      .getService
  }

  def loggingCredentials(): GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(LoggingScopes.LOGGING_WRITE)

  def logging(credentials: Credentials): Logging =
    new Logging.Builder(CCATransportFactory.getTransportInstance,
      JacksonFactory.getDefaultInstance,
      new HttpCredentialsAdapter(credentials))
      .setApplicationName("mainframe-util").build
}
