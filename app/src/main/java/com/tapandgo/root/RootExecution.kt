package com.tapandgo.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val exitCode: Int?, val stdout: String, val stderr: String, val timedOut: Boolean = false, val error: String? = null) {
    val ok: Boolean get() = exitCode == 0 && !timedOut && error == null
}

/** Process seam so execution logic is unit-testable without a rooted device. */
fun interface ShellRunner {
    suspend fun run(argv: List<String>, stdin: String?, timeoutMs: Long): ShellResult
}

class ProcessShellRunner : ShellRunner {
    override suspend fun run(argv: List<String>, stdin: String?, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(argv).start()
        } catch (e: java.io.IOException) {
            return@withContext ShellResult(null, "", "", error = "not found: ${argv.first()}")
        }
        try {
            coroutineScope {
                val out = async(Dispatchers.IO) { process.inputStream.readBounded() }
                val err = async(Dispatchers.IO) { process.errorStream.readBounded() }
                runCatching {
                    process.outputStream.bufferedWriter().use { w -> stdin?.let { w.write(it); w.write("\nexit\n") } }
                }
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) process.destroyForcibly()
                ShellResult(if (finished) process.exitValue() else null, out.await(), err.await(), timedOut = !finished)
            }
        } finally {
            process.destroy()
        }
    }

    private fun InputStream.readBounded(limit: Int = 256 * 1024): String {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = read(chunk)
            if (n < 0) break
            if (buf.size() < limit) buf.write(chunk, 0, minOf(n, limit - buf.size()))
        }
        return buf.toString(Charsets.UTF_8.name())
    }
}

enum class RootAccess { UNKNOWN, NOT_ROOTED, DENIED, GRANTED }
enum class RootImplementation { MAGISK, KERNELSU, APATCH, OTHER, UNKNOWN }

data class RootIndicators(val suPaths: List<String>, val managers: List<String>, val testKeys: Boolean) {
    val any: Boolean get() = suPaths.isNotEmpty() || managers.isNotEmpty()
}

data class RootStatus(
    val indicators: RootIndicators,
    val access: RootAccess = RootAccess.UNKNOWN,
    val implementation: RootImplementation = RootImplementation.UNKNOWN,
    val version: String? = null,
    val selinux: String? = null,
)

data class RootHistoryEntry(val time: Long, val label: String, val category: String, val outcome: String, val exitCode: Int?)

/** Parsing helpers, pure for tests. */
object RootParsing {
    fun implementation(suVersion: String): RootImplementation {
        val v = suVersion.lowercase()
        return when {
            "magisk" in v -> RootImplementation.MAGISK
            "kernelsu" in v || "ksu" in v -> RootImplementation.KERNELSU
            "apatch" in v -> RootImplementation.APATCH
            v.isBlank() -> RootImplementation.UNKNOWN
            else -> RootImplementation.OTHER
        }
    }

    /** `id` output of a root shell contains uid=0. */
    fun isRootId(idOutput: String) = Regex("""\buid=0\b""").containsMatchIn(idOutput)

    /** Removes values that look like secrets before output is shown in history-like places. */
    fun redact(text: String): String = text
        .replace(Regex("""(?i)(password|passwd|token|secret|key)\s*[=:]\s*\S+"""), "$1=•••")
}

/**
 * The only path to `su` in the app. Superuser is requested only when the user
 * asks (Check access or running a command), every command has a timeout,
 * exit status and output are captured, and history records category and
 * outcome only. Destructive commands require confirmation in the UI before
 * [execute] is called; nothing here is reachable from exported components.
 */
@Singleton
class RootExecutionService(
    private val runner: ShellRunner,
    private val historyFile: File?,
    private val packageManager: PackageManager?,
    private val sdk: Int = Build.VERSION.SDK_INT,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(ProcessShellRunner(), File(context.filesDir, "root/history.tsv"), context.packageManager)

    private val mutex = Mutex()
    private val _status = MutableStateFlow(RootStatus(detectIndicators()))
    val status: StateFlow<RootStatus> = _status.asStateFlow()
    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<RootHistoryEntry>> = _history.asStateFlow()

    fun detectIndicators(): RootIndicators {
        val paths = SU_PATHS.filter { runCatching { File(it).exists() }.getOrDefault(false) }
        val managers = MANAGER_PACKAGES.filter { pkg ->
            packageManager?.let { pm -> runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false) } ?: false
        }
        return RootIndicators(paths, managers, Build.TAGS?.contains("test-keys") == true)
    }

    /** Asks the root manager for access (it shows its own prompt) and identifies it. */
    suspend fun checkAccess(): RootStatus = mutex.withLock {
        val indicators = detectIndicators()
        val id = runner.run(listOf("su"), "id", ACCESS_TIMEOUT_MS)
        val access = when {
            id.error != null -> RootAccess.NOT_ROOTED
            id.ok && RootParsing.isRootId(id.stdout) -> RootAccess.GRANTED
            else -> RootAccess.DENIED
        }
        var impl = RootImplementation.UNKNOWN
        var version: String? = null
        var selinux: String? = null
        if (access == RootAccess.GRANTED) {
            val v = runner.run(listOf("su", "-v"), null, ACCESS_TIMEOUT_MS)
            version = v.stdout.trim().take(80).ifBlank { null }
            impl = RootParsing.implementation(v.stdout + " " + indicators.managers.joinToString(" "))
            selinux = runner.run(listOf("su"), "getenforce", ACCESS_TIMEOUT_MS).stdout.trim().ifBlank { null }
        } else {
            selinux = runner.run(listOf("getenforce"), null, ACCESS_TIMEOUT_MS).stdout.trim().ifBlank { null }
        }
        RootStatus(indicators, access, impl, version, selinux).also {
            _status.value = it
            addHistory("CheckAccess", "ACCESS", access.name, id.exitCode)
        }
    }

    suspend fun execute(cmd: RootCommand, timeoutMs: Long = COMMAND_TIMEOUT_MS): ShellResult {
        if (!cmd.supportedOnThisSdk(sdk)) return ShellResult(null, "", "", error = "needs Android API ${cmd.minSdk}+").also { addHistory(cmd.label, cmd.category.name, "UNSUPPORTED", null) }
        return runScript(cmd.script(), cmd.label, cmd.category.name, timeoutMs)
    }

    /** User-typed script from the Root console, confirmed in the UI immediately before. */
    suspend fun executeCustom(script: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): ShellResult {
        if (!CustomCommandPolicy.validate(script)) return ShellResult(null, "", "", error = "invalid command")
        return runScript(script, "Custom", "CUSTOM", timeoutMs)
    }

    private suspend fun runScript(script: String, label: String, category: String, timeoutMs: Long): ShellResult = mutex.withLock {
        if (_status.value.access != RootAccess.GRANTED) {
            return ShellResult(null, "", "", error = "root access not granted").also { addHistory(label, category, "NO_ROOT", null) }
        }
        val r = runner.run(listOf("su"), script, timeoutMs)
        val outcome = when {
            r.timedOut -> "TIMEOUT"
            r.error != null -> "ERROR"
            r.exitCode == 0 -> "OK"
            else -> "FAILED"
        }
        addHistory(label, category, outcome, r.exitCode)
        r.copy(stdout = RootParsing.redact(r.stdout), stderr = RootParsing.redact(r.stderr))
    }

    private fun addHistory(label: String, category: String, outcome: String, exit: Int?) {
        val e = RootHistoryEntry(System.currentTimeMillis(), label, category, outcome, exit)
        _history.update { (listOf(e) + it).take(MAX_HISTORY) }
        historyFile?.let { f ->
            runCatching {
                f.parentFile?.mkdirs()
                f.writeText(_history.value.joinToString("\n") { "${it.time}\t${it.label}\t${it.category}\t${it.outcome}\t${it.exitCode ?: ""}" })
            }
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        historyFile?.delete()
    }

    private fun loadHistory(): List<RootHistoryEntry> = historyFile?.takeIf { it.exists() }?.let { f ->
        runCatching {
            f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) null else RootHistoryEntry(p[0].toLongOrNull() ?: return@mapNotNull null, p[1], p[2], p[3], p[4].toIntOrNull())
            }
        }.getOrNull()
    }.orEmpty()

    companion object {
        const val ACCESS_TIMEOUT_MS = 20_000L
        const val COMMAND_TIMEOUT_MS = 15_000L
        const val MAX_HISTORY = 200
        val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su", "/vendor/bin/su", "/data/adb/ksud", "/data/adb/apd")
        val MANAGER_PACKAGES = listOf("com.topjohnwu.magisk", "io.github.huskydg.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu", "me.bmax.apatch")
    }
}
