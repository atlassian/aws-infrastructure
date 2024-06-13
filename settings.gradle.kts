rootProject.name = "aws-infrastructure"

plugins {
    id("com.gradle.develocity").version("3.17.4")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}
