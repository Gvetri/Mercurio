package org.mercurio.client.buildlogic

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

internal fun temporaryRepositoryCopy(source: File): File {
    val destination = Files.createTempDirectory("mercurio-self-check").toFile()
    try {
        copyRepository(source.toPath(), destination.toPath())
        initializeGitIndex(destination.toPath())
    } catch (error: Throwable) {
        destination.deleteRecursively()
        throw error
    }
    return destination
}

private fun initializeGitIndex(destination: Path) {
    runGit(destination, "init", "--quiet")
    runGit(destination, "add", "-A")
}

private fun runGit(
    directory: Path,
    vararg arguments: String,
) {
    val process =
        ProcessBuilder(listOf("git", "-C", directory.toString()) + arguments)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    check(exitCode == 0) { "Could not prepare the temporary repository copy: $output" }
}

private val excludedDirectoryNames = setOf(".git", ".gradle", ".kotlin", ".worktrees", ".idea", "build")

private fun copyRepository(
    source: Path,
    destination: Path,
) {
    Files.walkFileTree(
        source,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes,
            ): FileVisitResult {
                if (directory != source && directory.fileName.toString() in excludedDirectoryNames) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                Files.createDirectories(destination.resolve(source.relativize(directory).toString()))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: Path,
                attributes: BasicFileAttributes,
            ): FileVisitResult {
                val relative = source.relativize(file)
                if (relative.getName(0).toString() in excludedDirectoryNames) {
                    return FileVisitResult.CONTINUE
                }
                val target = destination.resolve(relative.toString())
                Files.createDirectories(target.parent)
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
        },
    )
    destination.resolve("gradlew").toFile().setExecutable(true)
}
