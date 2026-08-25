// Test reporting shared by the command line and CI.
//
// Gradle prints nothing useful about a passing test run, which makes "how many tests do we
// actually have?" an unanswerable question at exactly the moment it matters — reviewing a
// change that might have removed some. These tasks count what ran, from the JUnit XML the
// test task already writes, so the number is never hand-maintained.

import java.io.File

/** Reads every JUnit XML report under the project and totals the attributes. */
fun collectTestTotals(root: File): Map<String, Int> {
    val totals = linkedMapOf("tests" to 0, "failures" to 0, "errors" to 0, "skipped" to 0)
    val pattern = Regex("""<testsuite\b[^>]*""")
    val attr = { chunk: String, name: String ->
        Regex("""\b$name="(\d+)"""").find(chunk)?.groupValues?.get(1)?.toInt() ?: 0
    }
    root.walkTopDown()
        .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
        .forEach { file ->
            val head = pattern.find(file.readText())?.value ?: return@forEach
            totals["tests"] = totals.getValue("tests") + attr(head, "tests")
            totals["failures"] = totals.getValue("failures") + attr(head, "failures")
            totals["errors"] = totals.getValue("errors") + attr(head, "errors")
            totals["skipped"] = totals.getValue("skipped") + attr(head, "skipped")
        }
    return totals
}

tasks.register("testSummary") {
    group = "verification"
    description = "Counts every unit test that ran and writes the totals to build/test-summary.md."

    // Declared as a dependency rather than left to task ordering: without this the
    // summary can run first and report the previous run's numbers.
    dependsOn("testFdroidDebugUnitTest")

    val resultsDir = layout.buildDirectory.dir("test-results")
    val outputFile = layout.buildDirectory.file("test-summary.md")

    doLast {
        val dir = resultsDir.get().asFile
        if (!dir.exists()) {
            error("No test results found. Run the test task before testSummary.")
        }
        val totals = collectTestTotals(dir)
        val classes = dir.walkTopDown()
            .count { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
        val passed = totals.getValue("tests") -
            totals.getValue("failures") - totals.getValue("errors") - totals.getValue("skipped")

        val summary = buildString {
            appendLine("### Unit tests")
            appendLine()
            appendLine("| Total | Passed | Failed | Errors | Skipped | Suites |")
            appendLine("|------:|-------:|-------:|-------:|--------:|-------:|")
            appendLine(
                "| ${totals.getValue("tests")} | $passed | ${totals.getValue("failures")} " +
                    "| ${totals.getValue("errors")} | ${totals.getValue("skipped")} | $classes |"
            )
        }

        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(summary)

        logger.lifecycle("")
        logger.lifecycle("  Tests: ${totals.getValue("tests")}  ·  passed: $passed  ·  " +
            "failed: ${totals.getValue("failures")}  ·  errors: ${totals.getValue("errors")}  ·  " +
            "skipped: ${totals.getValue("skipped")}  ·  suites: $classes")
        logger.lifecycle("")

        if (totals.getValue("tests") == 0) {
            error("testSummary counted zero tests — the suite did not run.")
        }
    }
}

/**
 * A cheap gate that fails if the suite ever shrinks below the number of tests the project
 * is known to have. It catches the accidental deletion of a test file, which otherwise
 * shows up as a green build.
 */
tasks.register("verifyTestFloor") {
    group = "verification"
    description = "Fails if the total unit test count drops below the recorded floor."

    dependsOn("testFdroidDebugUnitTest")

    val floor = (project.findProperty("testFloor") as String?)?.toIntOrNull() ?: 0
    val resultsDir = layout.buildDirectory.dir("test-results")

    doLast {
        val dir = resultsDir.get().asFile
        if (!dir.exists()) error("No test results found. Run the test task first.")
        val total = collectTestTotals(dir).getValue("tests")
        if (total < floor) {
            error("Unit test count fell to $total, below the floor of $floor. " +
                "If tests were intentionally removed, lower testFloor in gradle.properties.")
        }
        logger.lifecycle("  Test floor OK: $total >= $floor")
    }
}
