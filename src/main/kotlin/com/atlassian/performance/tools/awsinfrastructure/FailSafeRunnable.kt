package com.atlassian.performance.tools.awsinfrastructure

internal class FailSafeRunnable(
    private val delegates: Iterable<Runnable>
) : Runnable {
    override fun run() {
        val exceptions = delegates.mapNotNull {
            try {
                it.run()
                null
            } catch (e: Exception) {
                e
            }
        }

        when {
            exceptions.isEmpty() -> return
            exceptions.size == 1 -> throw exceptions[0]
            else -> {
                val root = Exception("Multiple exceptions were thrown and are added suppressed into this one")
                exceptions.forEach { root.addSuppressed(it) }
                throw root
            }
        }
    }
}