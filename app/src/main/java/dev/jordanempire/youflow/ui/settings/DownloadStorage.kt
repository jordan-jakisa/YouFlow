package dev.jordanempire.youflow.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import dev.jordanempire.youflow.streams.io.StoredDirectoryHelper

/** The folders downloads are saved to, kept in the same preferences the download engine reads. */
object DownloadStorage {
    /** A readable name for the saved folder, or null when none is chosen yet. */
    fun describe(context: Context, key: String): String? {
        val saved = PreferenceManager.getDefaultSharedPreferences(context).getString(key, null)
        if (saved.isNullOrEmpty()) return null
        val uri = Uri.parse(saved)
        return if (ContentResolver.SCHEME_FILE == uri.scheme) uri.path else Uri.decode(uri.toString()).substringAfterLast("tree/")
    }

    /** Takes permission on [uri] and stores it under [key]. Returns false when it cannot be written. */
    fun save(context: Context, key: String, uri: Uri): Boolean = try {
        context.grantUriPermission(context.packageName, uri, StoredDirectoryHelper.PERMISSION_FLAGS)
        val dir = StoredDirectoryHelper(context, uri, null)
        if (!dir.canWrite()) {
            false
        } else {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, uri.toString()).apply()
            true
        }
    } catch (e: java.io.IOException) {
        false
    }
}
