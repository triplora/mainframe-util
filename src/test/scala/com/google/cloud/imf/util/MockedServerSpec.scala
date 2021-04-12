package com.google.cloud.imf.util

import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.socket.PortFactory
import org.mockserver.stop.Stop.stopQuietly
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec

abstract class MockedServerSpec extends AnyFlatSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  val localHost = "127.0.0.1"
  val localPort = PortFactory.findFreePort()
  val mockServer: ClientAndServer = startClientAndServer(localPort)

  override protected def beforeEach(): Unit = mockServer.reset()

  override protected def afterAll(): Unit = stopQuietly(mockServer)

}



