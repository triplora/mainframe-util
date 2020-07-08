/*
 * Copyright 2020 Google LLC All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
organization := "com.google.cloud.imf"
name := "mainframe-util"
version := "1.0.3"

scalaVersion := "2.13.1"

val exGuava = ExclusionRule(organization = "com.google.guava")
val exNs = ExclusionRule(organization = "io.grpc", name = "grpc-netty-shaded")

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.7.1",
  "org.scalatest" %% "scalatest" % "3.1.1" % Test
)

libraryDependencies ++= Seq("com.google.guava" % "guava" % "29.0-jre")

val grpcVersion = "1.30.2"

libraryDependencies ++= Seq(
  "com.google.api-client" % "google-api-client" % "1.30.9", // provided for google-cloud-bigquery
  "com.google.apis" % "google-api-services-logging" % "v2-rev20200619-1.30.9",
  "com.google.auto.value" % "auto-value-annotations" % "1.7.3", // provided for google-cloud-bigquery
  "com.google.http-client" % "google-http-client" % "1.35.0",
  "com.google.http-client" % "google-http-client-apache-v2" % "1.35.0",
  "com.google.http-client" % "google-http-client-jackson2" % "1.35.0",
  "com.google.cloud" % "google-cloud-bigquery" % "1.116.3",
  "com.google.cloud" % "google-cloud-storage" % "1.111.1",
  "com.google.oauth-client" % "google-oauth-client" % "1.30.6",
  "com.google.protobuf" % "protobuf-java" % "3.12.2",
  "com.google.protobuf" % "protobuf-java-util" % "3.12.2",
  "log4j" % "log4j" % "1.2.17",
  "org.apache.httpcomponents" % "httpclient" % "4.5.12",
  "org.apache.httpcomponents" % "httpcore" % "4.4.13",
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.slf4j" % "slf4j-log4j12" % "1.7.30",
  "io.opencensus" % "opencensus-api" % "0.26.0",
  "io.grpc" % "grpc-context" % grpcVersion,
  "io.grpc" % "grpc-core" % grpcVersion,
  "io.grpc" % "grpc-netty" % grpcVersion,
  "io.grpc" % "grpc-okhttp" % grpcVersion,
  "io.grpc" % "grpc-protobuf" % grpcVersion,
  "io.grpc" % "grpc-stub" % grpcVersion
).map(_ excludeAll(exGuava,exNs))

// Don't run tests during assembly
test in assembly := Seq()

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

// Exclude IBM jars from assembly jar since they will be provided
assemblyExcludedJars in assembly := {
  val IBMJars = Set("ibmjzos.jar", "ibmjcecca.jar", "dataaccess.jar")
  (fullClasspath in assembly).value
    .filter(file => IBMJars.contains(file.data.getName))
}

publishMavenStyle := true

resourceGenerators in Compile += Def.task {
  val file = (resourceDirectory in Compile).value / "mainframe-util-build.txt"
  val fmt = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
  val timestamp = fmt.format(new java.util.Date)
  IO.write(file, timestamp)
  Seq(file)
}.taskValue

scalacOptions ++= Seq(
  "-opt:l:inline",
  "-opt-inline-from:**",
  "-opt-warnings",
  "-deprecation"
)
