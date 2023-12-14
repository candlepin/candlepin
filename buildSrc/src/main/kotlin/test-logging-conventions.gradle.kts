import com.adarshr.gradle.testlogger.theme.ThemeType
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    id("com.adarshr.test-logger")
}

testlogger {
    theme = ThemeType.STANDARD
    showExceptions = true
    showStackTraces = true
    showFullStackTraces = project.hasProperty("full")
    showCauses = true
    slowThreshold = 2000
    showSummary = true
    showSimpleNames = false
    showPassed = true
    showSkipped = true
    showFailed = true
    showOnlySlow = false
    showStandardStreams = project.hasProperty("full")
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = project.hasProperty("full")
    logLevel = LogLevel.LIFECYCLE
}

tasks.withType<Test>().configureEach {
    val failedTests = mutableListOf<String>()

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(descriptor: TestDescriptor, result: TestResult) {
            if (result.resultType == TestResult.ResultType.FAILURE) {
                val failedTest = "${descriptor.className}::${descriptor.name}"
                logger.debug("Adding $failedTest to failedTests...")
                failedTests.add(failedTest)
            }
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            // will match the outermost suite
            if (suite.parent == null) {
                if (failedTests.isNotEmpty()) {
                    val out = project.serviceOf<StyledTextOutputFactory>()
                        .create("test-ouput")
                        .style(StyledTextOutput.Style.Failure)

                    out.text("Failed tests:").println()
                    failedTests.forEach { failedTest ->
                        out.text(failedTest).println()
                    }
                }
            }
        }
    })
}
