package com.vknxworks.memoctrl.data.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Attachment files kept on the phone, in a private folder per account. */
class FileStorage(private val context: Context) {

    private fun accountDir(accountKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8))
        val name = digest.take(12).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "attachments/$name").also { it.mkdirs() }
    }

    fun saveFile(accountKey: String, sourceUri: Uri, filename: String): Uri {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalArgumentException("Couldn't read the file")
        return input.use { saveFile(accountKey, it, filename) }
    }

    fun saveFile(accountKey: String, input: InputStream, filename: String): Uri {
        val file = File(accountDir(accountKey), safeName(filename))
        file.outputStream().use { input.copyTo(it) }
        return Uri.fromFile(file)
    }

    /** Deletes a file this class wrote. Anything outside the attachments folder is left alone. */
    fun deleteFile(uri: Uri) {
        val path = uri.path ?: return
        val file = File(path)
        val root = File(context.filesDir, "attachments").canonicalFile
        if (file.canonicalFile.startsWith(root)) file.delete()
    }

    fun deleteAccountFiles(accountKey: String) {
        accountDir(accountKey).deleteRecursively()
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
