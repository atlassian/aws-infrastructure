package com.atlassian.performance.tools.awsinfrastructure

internal class Ipv4Validator : (String) -> Boolean {
    override fun invoke(ip: String) = ip.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))
}