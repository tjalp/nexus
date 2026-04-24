package net.tjalp.nexus.backend.controlplane.provider

import java.io.BufferedReader

internal data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

internal object CommandExecution {
    fun runAndWait(command: List<String>, workingDirectory: String? = null): CommandResult {
        val process = ProcessBuilder(command)
            .apply {
                if (workingDirectory != null) {
                    directory(java.io.File(workingDirectory))
                }
            }
            .start()

        val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        val exit = process.waitFor()

        return CommandResult(exitCode = exit, stdout = stdout, stderr = stderr)
    }

    fun start(command: List<String>, workingDirectory: String? = null): Process {
        return ProcessBuilder(command)
            .apply {
                if (workingDirectory != null) {
                    directory(java.io.File(workingDirectory))
                }
            }
            .start()
    }

    fun shellCommand(command: String): List<String> {
        return if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            listOf("powershell.exe", "-NoProfile", "-Command", command)
        } else {
            listOf("sh", "-lc", command)
        }
    }
}

