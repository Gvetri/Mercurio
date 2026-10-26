import info.solidsoft.gradle.pitest.PitestTask
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.mercurio.client.buildlogic.QualityPolicy

plugins {
    id("org.jetbrains.kotlinx.kover")
    id("info.solidsoft.pitest")
}

val coverageScope = QualityPolicy.coverageFor(project.path)
val mutationScope = QualityPolicy.mutationFor(project.path)

kover {
    reports {
        verify {
            rule {
                bound {
                    coverageUnits = CoverageUnit.LINE
                    minValue = coverageScope.minLineCoveragePercent
                }
                bound {
                    coverageUnits = CoverageUnit.BRANCH
                    minValue = coverageScope.minBranchCoveragePercent
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("fixture.repositoryRoot", rootProject.projectDir.absolutePath)
}

tasks.named<PitestTask>("pitest") {
    targetClasses.set(listOf("${mutationScope.targetPackage}.*"))
    threads.set(2)
    mutationThreshold.set(mutationScope.minMutationScorePercent)
    testStrengthThreshold.set(mutationScope.minTestStrengthPercent)
    timestampedReports.set(false)
    outputFormats.set(setOf("XML", "HTML"))
    childProcessJvmArgs.set(listOf("-Dfixture.repositoryRoot=${rootProject.projectDir.absolutePath}"))
}
