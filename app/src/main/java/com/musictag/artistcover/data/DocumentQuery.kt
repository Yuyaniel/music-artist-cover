package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * SAF 目录的低层查询工具。
 *
 * 为什么不直接用 `DocumentFile`：它只是个 URI 壳子，每次读 `name` / `length` / `isDirectory`
 * 都会**单独发起一次 ContentResolver 查询**。列一个有 N 个文件的目录就要 N+1 次 binder 往返，
 * 几百个文件就能卡好几秒。
 *
 * 这里改成一次投影查询把需要的列全取回来，目录有几个文件都只发一次查询。
 */
object DocumentQuery {

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    class Child(
        val documentId: String,
        val name: String,
        val uri: Uri,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
    )

    /** 列出目录的直接子项。只发一次查询。 */
    fun listChildren(context: Context, treeUri: Uri, parentDocumentId: String): List<Child> {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        }.getOrNull() ?: return emptyList()

        val result = ArrayList<Child>()
        runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    result.add(
                        Child(
                            documentId = id,
                            name = name,
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = cursor.getLong(3),
                            lastModified = cursor.getLong(4),
                        ),
                    )
                }
            }
        }
        return result
    }

    /** 列出一个 tree Uri 根目录下的直接子项。 */
    fun listChildren(context: Context, treeUri: Uri): List<Child> {
        val rootId = treeDocumentId(treeUri) ?: return emptyList()
        return listChildren(context, treeUri, rootId)
    }

    fun treeDocumentId(treeUri: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()

    /** tree Uri 里根目录对应的文档 Uri，创建文件时作为父目录。 */
    fun rootDocumentUri(treeUri: Uri): Uri? =
        treeDocumentId(treeUri)?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }

    /** 读取某个文档的显示名（一次查询）。 */
    fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
        val target = rootDocumentUri(uri) ?: uri
        context.contentResolver
            .query(target, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /** 删除文档；SAF 下不需要额外查询。 */
    fun delete(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)

    /** 重命名文档；失败返回 false。 */
    fun rename(context: Context, uri: Uri, newName: String): Boolean = runCatching {
        DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
    }.getOrDefault(false)

    /** 在父目录下创建文件，返回新文档 Uri。 */
    fun createFile(context: Context, parentDocumentUri: Uri, mimeType: String, displayName: String): Uri? =
        runCatching {
            DocumentsContract.createDocument(context.contentResolver, parentDocumentUri, mimeType, displayName)
        }.getOrNull()
}
