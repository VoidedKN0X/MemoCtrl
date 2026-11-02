package com.vknxworks.memoctrl.data.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.str

/** Attachment files kept on the phone, in a private folder per account. */
class FileStorage(private val context: Context) {

    private fun accountDir(accountKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8))
        val name = digest.take(12).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "attachments/$name").also { it.mkdirs() }
    }

    fun saveFile(accountKey: String, sourceUri: Uri, filename: String): Uri {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalArgumentException(str(R.string.error_read_file))
        return input.use { saveFile(accountKey, it, filename) }
    }

    fun saveFile(accountKey: String, input: InputStream, filename: String): Uri {
        val file = File(accountDir(accountKey), safeName(filename))
        file.outputStream().use { input.copyTo(it) }
        return Uri.fromFile(file)
    }

    /** Whether [file] is inside the attachments folder, i.e. a file this class wrote. */
    fun isOwnFile(file: File): Boolean = runCatching {
        file.canonicalFile.startsWith(File(context.filesDir, "attachments").canonicalFile)
    }.getOrDefault(false)

    /** Deletes a file this class wrote. Anything outside the attachments folder is left alone. */
    fun deleteFile(uri: Uri) {
        val path = uri.path ?: return
        val file = File(path)
        val root = File(context.filesDir, "attachments").canonicalFile
        if (file.canonicalFile.startsWith(root)) file.delete()
    }

    /**
     * Copies a file this class wrote into [accountKey]'s folder and returns the copy's address, or [uri]
     * itself when it isn't one of these files (a server address, say) or couldn't be copied. The
     * original stays until [deleteAccountFilesExcept] removes it, so nothing is lost if the app stops
     * halfway.
     */
    fun copyToAccount(uri: String, accountKey: String): String {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return uri
        val file = File(parsed.path ?: return uri)
        val root = File(context.filesDir, "attachments").canonicalFile
        if (!file.exists() || !file.canonicalFile.startsWith(root)) return uri
        val target = File(accountDir(accountKey), file.name)
        if (target.canonicalPath == file.canonicalPath) return uri
        val copied = runCatching {
            file.copyTo(target, overwrite = true)
            target.length() == file.length()
        }.getOrDefault(false)
        if (!copied) runCatching { target.delete() }
        return if (copied) Uri.fromFile(target).toString() else uri
    }

    fun deleteAccountFiles(accountKey: String) {
        accountDir(accountKey).deleteRecursively()
    }

    /** Deletes [accountKey]'s files, except the ones [keep] (file:// addresses) still point to. */
    fun deleteAccountFilesExcept(accountKey: String, keep: Collection<String>) {
        val dir = accountDir(accountKey)
        val kept = keep.mapNotNull { u ->
            Uri.parse(u).takeIf { it.scheme == "file" }?.path?.let { runCatching { File(it).canonicalPath }.getOrNull() }
        }.toHashSet()
        dir.listFiles()?.forEach { f ->
            val path = runCatching { f.canonicalPath }.getOrNull()
            if (path == null || !kept.contains(path)) f.deleteRecursively()
        }
        if (dir.listFiles().isNullOrEmpty()) dir.delete()
    }

    companion object {
        /** A file name that can't leave its folder or hide control characters. */
        fun safeName(name: String): String {
            val cleaned = name.map { ch ->
                when {
                    ch == '/' || ch == '\\' || ch == ':' || ch.code < 32 || ch == '\u007f' -> '_'
                    else -> ch
                }
            }.joinToString("").trim().trimStart('.')
            return cleaned.take(120).ifBlank { "file" }
        }
    }
}
