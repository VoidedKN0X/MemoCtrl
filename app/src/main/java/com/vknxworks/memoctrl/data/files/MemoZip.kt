package com.vknxworks.memoctrl.data.files

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.vknxworks.memoctrl.BuildConfig
import com.vknxworks.memoctrl.data.api.ApiJson
import com.vknxworks.memoctrl.data.api.await
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.ResourceEntity
import com.vknxworks.memoctrl.data.sync.SyncingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Memos as a ZIP file, in the Memos Export Format (the format Memos 0.31's own "Export memos" writes,
 * docs/design/memos-export-format.md in the Memos project): `manifest.json`, `memos/<uid>.json` with a
 * memo's dates, visibility, pin, location and attachment list, `memos/<uid>.md` with its exact text, and
 * `attachments/<uid>/<file name>` with the files.
 *
 * The export is written on the phone from the memos MemoCtrl holds, so it works with every server
 * version and without a server. The import reads that format, and also any other ZIP of Markdown or
 * text files (each file becomes one memo). Imported memos are new memos of the account in use and sync
 * like anything else you write; a memo that is already there is skipped.
 */
object MemoZip {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; isLenient = true }

    private const val FORMAT = "memos-export"
    private const val LEGACY_FORMAT = "memos-archive"
    private val UID = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,34}[a-zA-Z0-9])?$")

    /** Limits for a file being imported, so a broken or hostile ZIP can't fill the phone. */
    private const val MAX_MEMOS = 50_000
    private const val MAX_TEXT_BYTES = 1L * 1024 * 1024
    private const val MAX_FILE_BYTES = 512L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    @Serializable
    private data class Generator(val name: String = "", val version: String = "")

    @Serializable
    private data class ScopeUser(val username: String = "", val displayName: String = "")

    @Serializable
    private data class Scope(val kind: String = "USER", val user: ScopeUser? = null)

    @Serializable
    private data class Counts(val memos: Int = 0, val attachments: Int = 0)

    @Serializable
    private data class Manifest(
        val format: String = FORMAT,
        val formatVersion: String = "1.0",
        val generator: Generator = Generator(),
        val exportTime: String = "",
        val scope: Scope = Scope(),
        val counts: Counts? = null,
    )

    @Serializable
    private data class AttachmentRecord(
        val uid: String = "",
        val filename: String = "",
        val type: String = "application/octet-stream",
        val size: Long = 0,
        val sha256: String? = null,
        val path: String? = null,
        val externalLink: String? = null,
        val createTime: String = "",
    )

    @Serializable
    private data class MemoRecord(
        val uid: String = "",
        val creator: String = "",
        val createTime: String = "",
        val updateTime: String = "",
        val state: String = "NORMAL",
        val visibility: String = "PRIVATE",
        val pinned: Boolean = false,
        val contentPath: String = "",
        val tags: List<String>? = null,
        val location: MemoLocation? = null,
        val parent: String? = null,
        val attachments: List<AttachmentRecord>? = null,
    )

    data class ExportResult(val memos: Int, val attachments: Int, val missingAttachments: Int)

    private fun stamp(t: Instant): String = DateTimeFormatter.ISO_INSTANT.format(t.truncatedTo(ChronoUnit.SECONDS))

    /** The Memos id of a memo that is on a server, or one made from the phone's own id. Always a valid UID. */
    private fun uidOf(preferred: String?, fallback: String, taken: MutableSet<String>): String {
        val wanted = preferred?.substringBefore('|')?.substringAfterLast('/')?.takeIf { UID.matches(it) }
            ?: fallback.filter { it.isLetterOrDigit() && it.code < 128 }.take(32).ifEmpty { "memo" }
        var uid = wanted
        var n = 2
        while (!taken.add(uid)) uid = wanted.take(30) + (n++)
        return uid
    }

    /** The format's safe file name: no separators or control characters, no leading or trailing dots and spaces. */
    private fun safeFilename(name: String): String {
        val cleaned = name.map { if (it == '/' || it == '\\' || it == ':' || it.code < 32) '_' else it }.joinToString("").trim(' ', '.')
        val stem = cleaned.substringBefore('.').uppercase()
        val reserved = stem in setOf("CON", "PRN", "AUX", "NUL") || Regex("^(COM|LPT)[1-9]$").matches(stem)
        return (if (reserved) "_$cleaned" else cleaned).ifEmpty { "file" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The attachment's file on the phone: the kept copy, or a download into [scratch]. Null when neither works. */
    private suspend fun fileOf(resource: ResourceEntity, http: OkHttpClient?, scratch: File): File? {
        for (uri in listOf(resource.localUri, resource.uri)) {
            val u = uri?.let(Uri::parse) ?: continue
            if (u.scheme == "file") u.path?.let(::File)?.takeIf { it.isFile }?.let { return it }
        }
        if (http == null || !(resource.uri.startsWith("https://") || resource.uri.startsWith("http://"))) return null
        return runCatching {
            val target = File(scratch, resource.identifier.filter { it.isLetterOrDigit() }.take(40) + ".bin")
            http.newCall(Request.Builder().url(resource.uri).build()).await().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            }
            target
        }.getOrNull()
    }

    /**
     * Writes every memo of [repo]'s account (also archived ones) to [out]. Attachments that are only on
     * the server are downloaded for it with [http] (null for an account without a server); one that
     * can't be fetched is left out of the file and counted in [ExportResult.missingAttachments].
     */
    suspend fun export(
        context: Context,
        repo: SyncingRepository,
        http: OkHttpClient?,
        username: String,
        displayName: String,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        val memos = (repo.listMemos() + repo.listArchivedMemos()).filter { !it.isDeleted }.sortedBy { it.date }
        val now = Instant.now()
        val scratch = File(context.cacheDir, "export-${now.toEpochMilli()}").also { it.mkdirs() }
        var written = 0
        var missing = 0
        try {
            ZipOutputStream(out.buffered()).use { zip ->
                fun entry(name: String): ZipEntry = ZipEntry(name).apply { time = now.toEpochMilli() }
                zip.putNextEntry(entry("manifest.json"))
                zip.write(
                    json.encodeToString(
                        Manifest.serializer(),
                        Manifest(
                            generator = Generator("MemoCtrl", BuildConfig.VERSION_NAME),
                            exportTime = stamp(now),
                            scope = Scope(user = ScopeUser(username, displayName)),
                            counts = Counts(memos = memos.size, attachments = memos.sumOf { it.resources.size }),
                        ),
                    ).toByteArray(),
                )
                zip.closeEntry()
                val memoUids = HashSet<String>()
                val fileUids = HashSet<String>()
                memos.forEachIndexed { i, memo ->
                    coroutineContext.ensureActive()
                    onProgress(i, memos.size)
                    val uid = uidOf(memo.remoteId, memo.identifier, memoUids)
                    val records = ArrayList<AttachmentRecord>()
                    for (r in memo.resources) {
                        val fileUid = uidOf(r.remoteId, r.identifier, fileUids)
                        val type = r.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                        val file = fileOf(r, http, scratch)
                        if (file == null) {
                            missing++
                            continue
                        }
                        val path = "attachments/$fileUid/${safeFilename(r.filename.ifBlank { "file" })}"
                        records += AttachmentRecord(fileUid, r.filename.ifBlank { "file" }, type, file.length(), sha256(file), path, null, stamp(r.date))
                        zip.putNextEntry(entry(path))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        written++
                        if (file.parentFile == scratch) file.delete()
                    }
                    val record = MemoRecord(
                        uid = uid,
                        creator = username,
                        createTime = stamp(memo.date),
                        updateTime = stamp(memo.lastSyncedAt ?: memo.lastModified),
                        state = if (memo.archived) "ARCHIVED" else "NORMAL",
                        visibility = memo.visibility.name,
                        pinned = memo.pinned,
                        contentPath = "memos/$uid.md",
                        location = memo.location,
                        attachments = records.takeIf { it.isNotEmpty() },
                    )
                    zip.putNextEntry(entry("memos/$uid.json"))
                    zip.write(json.encodeToString(MemoRecord.serializer(), record).toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(entry("memos/$uid.md"))
                    zip.write(memo.content.toByteArray())
                    zip.closeEntry()
                }
                onProgress(memos.size, memos.size)
            }
        } finally {
            scratch.deleteRecursively()
        }
        ExportResult(memos.size, written, missing)
    }

    // ---------- Import ----------

    /** One memo found in a file, ready to be created. */
    class ImportMemo(
        val uid: String?,
        val content: String,
        val date: Instant?,
        val visibility: MemoVisibility,
        val pinned: Boolean,
        val archived: Boolean,
        val location: MemoLocation?,
        val files: List<ImportFile>,
    )

    class ImportFile(val filename: String, val type: String?, val file: File)

    /**
     * What a ZIP holds, read but not imported yet. [memos] are the ones that would be created; [existing]
     * were left out because the account already has them; [comments] because comments can only be made
     * on a server, under the memo they belong to. Call [discard] when done with it.
     */
    class Plan(
        val memos: List<ImportMemo>,
        val existing: Int,
        val comments: Int,
        val memosFormat: Boolean,
        val exportedAt: String?,
        private val scratch: File,
    ) {
        val attachments: Int get() = memos.sumOf { it.files.size }
        fun discard() { scratch.deleteRecursively() }
    }

    class NotAMemoZip : Exception()

    /** An entry name that stays inside the archive: relative, no "..", no backslash, colon or control character. */
    private fun safeEntry(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && name.none { it == '\\' || it == ':' || it.code < 32 } &&
            name.split('/').none { it == "." || it == ".." }

    private fun InputStream.readCapped(max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }

    private fun instant(text: String?): Instant? = text?.takeIf { it.isNotBlank() }?.let { t ->
        runCatching { java.time.OffsetDateTime.parse(t).toInstant() }.getOrNull() ?: runCatching { Instant.parse(t) }.getOrNull()
    }

    /**
     * Reads the ZIP at [uri] into a [Plan]. Attachment files are unpacked into the app's cache until the
     * plan is imported or discarded. Throws [NotAMemoZip] for a file with no memos in it.
     */
    suspend fun read(context: Context, uri: Uri, repo: SyncingRepository): Plan = withContext(Dispatchers.IO) {
        val scratch = File(context.cacheDir, "import-${System.currentTimeMillis()}").also { it.mkdirs() }
        try {
            var manifest: Manifest? = null
            val records = LinkedHashMap<String, MemoRecord>()
            val texts = HashMap<String, String>()
            val loose = ArrayList<Pair<String, Long>>()
            val files = HashMap<String, File>()
            var total = 0L
            val input = context.contentResolver.openInputStream(uri) ?: throw NotAMemoZip()
            ZipInputStream(input.buffered()).use { zip ->
                var n = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    coroutineContext.ensureActive()
                    val name = e.name
                    if (e.isDirectory || !safeEntry(name)) continue
                    val lower = name.lowercase()
                    when {
                        name == "manifest.json" ->
                            manifest = zip.readCapped(MAX_TEXT_BYTES)?.let { runCatching { json.decodeFromString(Manifest.serializer(), String(it)) }.getOrNull() }
                        name.startsWith("memos/") && lower.endsWith(".json") ->
                            zip.readCapped(MAX_TEXT_BYTES)?.let { bytes ->
                                runCatching { json.decodeFromString(MemoRecord.serializer(), String(bytes)) }.getOrNull()?.let { records[name] = it }
                            }
                        lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt") ->
                            zip.readCapped(MAX_TEXT_BYTES)?.let { bytes ->
                                texts[name] = String(bytes).removePrefix("﻿")
                                loose += name to e.time
                            }
                        name.startsWith("attachments/") -> {
                            val target = File(scratch, "f${n++}")
                            var size = 0L
                            target.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val read = zip.read(buf)
                                    if (read < 0) break
                                    size += read
                                    total += read
                                    if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) throw NotAMemoZip()
                                    out.write(buf, 0, read)
                                }
                            }
                            files[name] = target
                        }
                    }
                    if (records.size > MAX_MEMOS || loose.size > MAX_MEMOS) throw NotAMemoZip()
                }
            }

            val mine = (repo.listMemos() + repo.listArchivedMemos())
            val haveUids = mine.mapNotNullTo(HashSet()) { it.remoteId?.substringBefore('|')?.substringAfterLast('/') }
            val haveTexts = mine.mapTo(HashSet()) { it.content.trim() to it.date.epochSecond }
            val m = manifest
            val memosFormat = m != null && (m.format == FORMAT || m.format == LEGACY_FORMAT) && m.formatVersion.substringBefore('.') == "1"
            val out = ArrayList<ImportMemo>()
            var existing = 0
            var comments = 0
            if (memosFormat) {
                for (record in records.values.sortedBy { instant(it.createTime) ?: Instant.EPOCH }) {
                    val content = texts[record.contentPath] ?: continue
                    if (!record.parent.isNullOrBlank()) { comments++; continue }
                    val date = instant(record.createTime)
                    if (record.uid in haveUids || (date != null && (content.trim() to date.epochSecond) in haveTexts)) { existing++; continue }
                    out += ImportMemo(
                        uid = record.uid,
                        content = content,
                        date = date,
                        // A memo for a space can't be put back in its space from here: it comes in private.
                        visibility = runCatching { MemoVisibility.valueOf(record.visibility) }.getOrDefault(MemoVisibility.PRIVATE)
                            .let { if (it == MemoVisibility.SPACE) MemoVisibility.PRIVATE else it },
                        pinned = record.pinned,
                        archived = record.state == "ARCHIVED",
                        location = record.location?.takeIf { it.hasValidCoordinates },
                        files = record.attachments.orEmpty().mapNotNull { a ->
                            val file = a.path?.let { files[it] } ?: return@mapNotNull null
                            ImportFile(a.filename.ifBlank { a.path.orEmpty().substringAfterLast('/') }, a.type.takeIf { it.isNotBlank() }, file)
                        },
                    )
                }
            } else {
                // Not a Memos export: every Markdown or text file is one memo, dated like the file.
                for ((name, time) in loose.sortedBy { it.second }) {
                    val content = texts[name]?.trim().orEmpty()
                    if (content.isEmpty()) continue
                    val date = time.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
                    if (date != null && (content to date.epochSecond) in haveTexts) { existing++; continue }
                    if (mine.any { it.content.trim() == content }) { existing++; continue }
                    out += ImportMemo(null, content, date, MemoVisibility.PRIVATE, pinned = false, archived = false, location = null, files = emptyList())
                }
            }
            if (out.isEmpty() && existing == 0 && comments == 0) throw NotAMemoZip()
            Plan(out, existing, comments, memosFormat, m?.exportTime?.takeIf { memosFormat }, scratch)
        } catch (e: Throwable) {
            scratch.deleteRecursively()
            if (e is java.util.zip.ZipException) throw NotAMemoZip()
            throw e
        }
    }

    /** Creates the memos of [plan] in [repo]'s account. Returns how many were created. */
    suspend fun importPlan(plan: Plan, repo: SyncingRepository, onProgress: (done: Int, total: Int) -> Unit): Int = withContext(Dispatchers.IO) {
        var done = 0
        try {
            for (memo in plan.memos) {
                coroutineContext.ensureActive()
                onProgress(done, plan.memos.size)
                val resources = memo.files.map { f ->
                    val type = f.type ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.filename.substringAfterLast('.', "").lowercase())
                    repo.createResource(f.filename, type?.toMediaTypeOrNull(), Uri.fromFile(f.file))
                }
                repo.createMemo(
                    content = memo.content,
                    visibility = memo.visibility,
                    resources = resources,
                    location = memo.location,
                    date = memo.date,
                    pinned = memo.pinned,
                    archived = memo.archived,
                )
                done++
            }
            onProgress(done, plan.memos.size)
        } finally {
            plan.discard()
        }
        done
    }

    fun fileName(username: String): String {
        val time = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now())
        val who = username.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "memoctrl" }
        return "memos-export-$who-$time.zip"
    }
}
