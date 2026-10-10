package com.musictag.artistcover.data

import android.util.Log

/**
 * 应用日志。
 *
 * 原先把日志塞进一个 Compose 状态列表里等界面订阅，但日志页早已被移除，
 * 那份 400 条环形缓冲就成了纯负担：每次调用都要格式化时间、在同步块里改列表，
 * 而且只在内存里打转，真出问题时根本捞不出来。
 *
 * 现在统一转发到 logcat（`adb logcat -s MusicArtistCover`），日志能真正被看到，
 * 调用点一行都不用改。
 */
object AppLog {

    private const val TAG = "MusicArtistCover"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }
}
