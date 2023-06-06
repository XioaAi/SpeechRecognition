package com.neunit.translation.helper

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


/**
 * @Description
 * @Author ZhaoXiudong
 * @Date 05-31-2023 周三 11:22
 */
class FileHelper {
    companion object {
        fun getFileName(uri: Uri, context: Context): String? {
            var result: String? = null
            var cursor: Cursor? = null
            try {
                if (uri.scheme.equals("content")) {
                    cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
                }
                if (result == null) {
                    result = uri.path
                    val cut = result!!.lastIndexOf('/')
                    if (cut != -1) {
                        result = result.substring(cut + 1)
                    }
                }
            } catch (ex: Exception) {
                Log.e("FileHelper", "获取文件名字出现异常: $ex")
            } finally {
                cursor?.close()
            }
            return result
        }

        fun getFilePath(uri: Uri, context: Context): String {
            var fos: FileOutputStream? = null
            var ins: InputStream? = null
            val fileName = getFileName(uri, context)
            var path: String =
                context.cacheDir.absolutePath + "/file_picker/" + (fileName ?: System.currentTimeMillis())
            val file = File(path)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                try {
                    fos = FileOutputStream(path)
                    val out = BufferedOutputStream(fos)
                    ins = context.contentResolver.openInputStream(uri)
                    ins?.let { input ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (input.read(buffer).also { len = it } >= 0) {
                            out.write(buffer, 0, len)
                        }
                    }
                    out.flush()
                } catch (ex: Exception) {
                    Log.e("FileHelper", "获取文件路径出现异常: $ex")
                    if (file.exists()) {
                        file.delete()
                    }
                    path = ""
                } finally {
                    try {
                        fos?.fd?.sync()
                    } catch (ex: Exception) {
                        Log.e("FileHelper", "获取文件路径出现异常: $ex")
                    }
                    fos?.close()
                    ins?.close()
                }
            }
            return path
        }
    }
}