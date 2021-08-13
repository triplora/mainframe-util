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
version := "2.1.8"

scalaVersion := "2.13.1"

val grpcVersion = "1.33.1"

val exGuava = ExclusionRule(organization = "com.google.guava")
val exOc = ExclusionRule(organization = "io.opencensus")
val exProto = ExclusionRule(organization = "com.google.protobuf")
val exGrpc = ExclusionRule(organization = "io.grpc")
val exC1 = ExclusionRule(organization = "com.google.cloud", name = "google-cloud-core")
val exC2 = ExclusionRule(organization = "com.google.cloud", name = "google-cloud-core-http")
val exNettyShaded = ExclusionRule(organization = "io.grpc", name = "grpc-netty-shaded")
val exConscrypt = ExclusionRule(organization = "org.conscrypt", name = "conscrypt-openjdk-uber")
val exProtos = ExclusionRule(organization = "com.google.api.grpc", name = "proto-google-common-protos")

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "30.0-jre",
  "com.github.scopt" %% "scopt" % "3.7.1",
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "org.mock-server" % "mockserver-netty" % "5.11.2" % Test,
  "org.mock-server" % "mockserver-client-java" % "5.11.2" % Test
)

libraryDependencies ++= Seq(
  "com.google.cloud" % "google-cloud-core-http" % "1.94.0",
  "com.google.http-client" % "google-http-client-apache-v2" % "1.38.0",
  "com.google.api" % "gax-grpc" % "1.60.0",
).map(_ excludeAll (exGuava))
  .map(_ excludeAll (exGrpc))

libraryDependencies ++= Seq(
  "com.google.apis" % "google-api-services-logging" % "v2-rev20201101-1.30.10",
  "com.google.cloud" % "google-cloud-bigquery" % "1.124.3",
  "com.google.cloud" % "google-cloud-bigquerystorage" % "1.6.1",
  "com.google.cloud" % "google-cloud-storage" % "1.113.3",
  "org.apache.avro" % "avro" % "1.7.7",
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.slf4j" % "slf4j-log4j12" % "1.7.30",
  "org.slf4j" % "jul-to-slf4j" % "1.7.30"
).map(_ excludeAll(exGuava, exProto, exProtos, exGrpc, exC1, exC2, exConscrypt, exNettyShaded))

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-all" % grpcVersion
).map(_ excludeAll(exGuava, exProto, exProtos, exC1, exC2, exConscrypt, exNettyShaded))


// Don't run tests during assembly
test in assembly := Seq()

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

// Exclude IBM jars from assembly jar since they will be provided
assemblyExcludedJars in assembly := {
  val IBMJars = Set("ibmjzos.jar", "ibmjcecca.jar")
  (fullClasspath in assembly).value
    .filter(file => IBMJars.contains(file.data.getName))
}

publishMavenStyle := false

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
