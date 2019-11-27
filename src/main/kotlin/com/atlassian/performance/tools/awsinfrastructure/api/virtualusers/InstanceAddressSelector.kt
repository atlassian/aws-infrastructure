package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.ec2.model.Instance
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.ConnectException
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors


class InstanceAddressSelector {
    companion object {
        private val logger: Logger = LogManager.getLogger(this::class.java)

        private val reachableAddresses: ConcurrentHashMap<String, String> = ConcurrentHashMap();
        private val executor = Executors.newCachedThreadPool()

        internal fun getReachableIpAddress(instance: Instance, port: Int = 22): String {
            return reachableAddresses.computeIfAbsent(instance.publicIpAddress) {
                val addressesToCheck = arrayOf(instance.publicIpAddress, instance.privateIpAddress)

                getReachableIpAddress(port, *(addressesToCheck))
            }
        }

        private fun getReachableIpAddress(port: Int, vararg addresses: String): String {
            val completionService: CompletionService<String> = ExecutorCompletionService<String>(executor)

            val futures = addresses.map { address ->
                completionService.submit {
                    isAddressReachable(port, address)
                }
            }.toMutableList()

            var pending = addresses.size
            try {
                do {
                    val reachableAddress = completionService.take().get()
                    if (reachableAddress != null) {
                        return reachableAddress
                    }
                } while (--pending > 0)
            } finally {
                futures.forEach { it.cancel(true)}
            }
            throw ConnectException("Neither of " + addresses.contentToString() + " responded on port $port")
        }

        private fun isAddressReachable(port: Int, testedAddress: String): String? {
            return try {
                IdempotentAction("connect to $testedAddress on port $port") {
                    logger.debug("Trying to connect to $testedAddress:$port")
                    Socket(testedAddress, port).use {  }
                    logger.debug("Connected to $testedAddress:$port")
                }.retry(
                    maxAttempts = 4,
                    backoff = ExponentialBackoff(
                        baseBackoff = Duration.ofSeconds(1)
                    )
                )
                testedAddress
            } catch (e: Exception) {
                null
            }
        }
    }

}
