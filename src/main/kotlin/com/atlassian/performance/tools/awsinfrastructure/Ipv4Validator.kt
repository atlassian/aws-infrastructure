package com.atlassian.performance.tools.awsinfrastructure

import java.util.function.Predicate

internal class Ipv4Validator : Predicate<String> {
    override fun test(ip: String) = ip.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))
}
