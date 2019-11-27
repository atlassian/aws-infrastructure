package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.ec2.model.Instance
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import java.net.Inet4Address
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch

internal class InstanceAddressSelectorTest {
    private lateinit var serverSocket: ServerSocket
    private var isServerRunning: CountDownLatch = CountDownLatch(1)

    @Before
    fun setup() {
        serverSocket = ServerSocket(0)
        val thread = Thread {
            try {
                isServerRunning.countDown()
                serverSocket.accept()
                serverSocket.close()
            } catch (e: Exception) {
            }
        }
        thread.start()
    }

    @After
    fun tearDown() {
        try {
            serverSocket.close()
        } catch (ignored: Exception) {
        }
    }

    @Test
    fun selectsCorrectAddress() {
        isServerRunning.await()
        val loopback = Inet4Address.getLoopbackAddress().hostAddress
        val reachableIpAddress = InstanceAddressSelector.getReachableIpAddress(newInstance("240.0.0.1", loopback), serverSocket.localPort)
        assertThat(reachableIpAddress, equalTo(loopback))
    }

    private fun newInstance(publicIp: String, privateIp: String) =
        Instance()
            .withPublicIpAddress(publicIp)
            .withPrivateIpAddress(privateIp)
}
