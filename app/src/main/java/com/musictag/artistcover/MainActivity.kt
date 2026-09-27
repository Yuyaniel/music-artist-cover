package com.musictag.artistcover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.musictag.artistcover.data.AppLog
import com.musictag.artistcover.data.DocumentQuery
import com.musictag.artistcover.data.ExtraImageFile
import com.musictag.artistcover.data.ImageDownloader
import com.musictag.artistcover.data.LibraryCheckResult
import com.musictag.artistcover.data.LibraryChecker
import com.musictag.artistcover.data.MatchEngine
import com.musictag.artistcover.data.MetadataReader
import com.musictag.artistcover.data.NameMatcher
import com.musictag.artistcover.data.Prefs
import com.musictag.artistcover.data.SaveOutcome
import com.musictag.artistcover.data.SavedFiles
import com.musictag.artistcover.data.SongCache
import com.musictag.artistcover.data.downloadStateOf
import com.musictag.artistcover.data.SongScanner
import com.musictag.artistcover.model.ArtistGroup
import com.musictag.artistcover.model.DownloadState
import com.musictag.artistcover.model.MatchState
import com.musictag.artistcover.model.MatchTarget
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import com.musictag.artistcover.theme.MusicArtistTheme
import com.musictag.artistcover.ui.ArtistSort
import com.musictag.artistcover.ui.MatchPage
import com.musictag.artistcover.ui.SingleMatchSheet
import com.musictag.artistcover.ui.SongPage
import com.musictag.artistcover.ui.SongSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicArtistTheme {
                AppRoot()
            }
        }
    }
}

private enum class AppTab(val labelRes: Int) {
    Songs(R.string.tab_songs),
    Settings(R.string.tab_match);

    val icon: ImageVector
        get() = when (this) {
            Songs -> Icons.AutoMirrored.Filled.List
            Settings -> Icons.Default.Settings
        }
}

/** 「关于」里跳转的项目仓库。 */
private const val REPO_URL = "https://github.com/Yuyaniel/music-artist-cover"

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember { Prefs(context) }

    var tab by remember { mutableStateOf(AppTab.Songs) }
    var songTreeUri by remember { mutableStateOf(prefs.songTreeUri?.let(Uri::parse)) }
    var outputTreeUri by remember { mutableStateOf(prefs.outputTreeUri?.let(Uri::parse)) }
    var songFolderName by remember { mutableStateOf<String?>(null) }
    var outputFolderName by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var cachedAt by remember { mutableStateOf<Long?>(null) }
    var savedFiles by remember { mutableStateOf(SavedFiles.EMPTY) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedArtists by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var platformOrder by remember { mutableStateOf(prefs.platformOrder()) }
    var enabledPlatforms by remember { mutableStateOf(prefs.enabledPlatforms()) }
    var overwrite by remember { mutableStateOf(prefs.overwriteExisting) }
    var scanning by remember { mutableStateOf(false) }
    var matching by remember { mutableStateOf(false) }
    var progressDone by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var matchTarget by remember { mutableStateOf<MatchTarget?>(null) }
    var songSort by remember { mutableStateOf(SongSort.from(prefs.songSort)) }
    var artistSort by remember { mutableStateOf(ArtistSort.from(prefs.artistSort)) }
    var checkResult by remember { mutableStateOf<LibraryCheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    /** 保存结果提示：失败时把具体原因说出来，而不是笼统的「没有新文件」。 */
    fun notifySaveOutcome(outcome: SaveOutcome) {
        notify(
            when {
                outcome.savedCount > 0 -> context.getString(R.string.save_done, outcome.savedCount)
                outcome.error != null -> outcome.error
                else -> context.getString(R.string.save_none)
            },
        )
    }

    fun folderNameOf(uri: Uri): String? =
        DocumentQuery.displayNameOf(context, uri) ?: uri.lastPathSegment

    fun togglePlatform(platform: Platform) {
        enabledPlatforms = if (platform in enabledPlatforms) enabledPlatforms - platform else enabledPlatforms + platform
        prefs.setEnabledPlatforms(enabledPlatforms)
    }

    /** 调整平台优先级：delta = -1 上移，+1 下移。 */
    fun movePlatform(platform: Platform, delta: Int) {
        val list = platformOrder.toMutableList()
        val index = list.indexOf(platform)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val moving = list[index]
        list[index] = list[target]
        list[target] = moving
        platformOrder = list
        prefs.setPlatformOrder(list)
    }

    /** 重新读取保存文件夹索引：只发一次查询，几毫秒返回。 */
    fun refreshSavedFiles() {
        scope.launch {
            savedFiles = withContext(Dispatchers.IO) { SavedFiles.load(context, outputTreeUri) }
        }
    }

    val scanFolder: (Uri, String?) -> Unit = { uri, name ->
        scope.launch {
            scanning = true
            songFolderName = name ?: folderNameOf(uri)
            AppLog.i("开始扫描歌曲文件夹：${songFolderName ?: uri.lastPathSegment}")
            val items = withContext(Dispatchers.IO) {
                SongScanner.scan(context, uri).map { entry ->
                    val meta = MetadataReader.read(context, entry.uri, entry.name)
                    SongItem(
                        id = entry.uri.toString(),
                        uri = entry.uri,
                        displayName = entry.name,
                        sizeBytes = entry.sizeBytes,
                        addedAt = entry.lastModified,
                        title = meta.title,
                        artist = meta.artist,
                        artists = meta.artists,
                        artistSource = meta.kind,
                    )
                }
            }
            songs = items
            selectedIds = selectedIds.filter { id -> items.any { it.id == id } }.toSet()
            scanning = false
            if (items.isEmpty()) {
                AppLog.w("该文件夹内没有找到支持的音频文件")
            } else {
                val now = System.currentTimeMillis()
                cachedAt = now
                withContext(Dispatchers.IO) { SongCache.save(context, uri.toString(), items) }
                val artistCount = items.sumOf { it.artistList.size }
                AppLog.i("扫描完成并写入缓存：共 ${items.size} 首，识别出 $artistCount 位歌手")
            }
        }
    }

    LaunchedEffect(Unit) {
        songTreeUri?.let { uri ->
            songFolderName = withContext(Dispatchers.IO) { folderNameOf(uri) }
            val cached = withContext(Dispatchers.IO) { SongCache.load(context, uri.toString()) }
            when {
                cached != null && cached.songs.isNotEmpty() && cached.songs.all { it.addedAt == 0L } -> {
                    // 旧版缓存没记录文件修改时间，重扫一次补齐，之后时间排序才可用
                    AppLog.i("缓存缺少添加时间，重新扫描一次以支持时间排序")
                    scanFolder(uri, null)
                }

                // 先用缓存秒开，只有手动刷新才重新扫描
                cached != null && cached.songs.isNotEmpty() -> {
                    songs = cached.songs
                    cachedAt = cached.savedAt
                    AppLog.i("已加载扫描缓存：${cached.songs.size} 首（未重新扫描）")
                }

                else -> scanFolder(uri, null)
            }
        }
        outputTreeUri?.let { uri ->
            outputFolderName = withContext(Dispatchers.IO) { folderNameOf(uri) }
            refreshSavedFiles()
        }
    }

    val songFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.songTreeUri = uri.toString()
            songTreeUri = uri
            cachedAt = null
            scanFolder(uri, null)
        }
    }

    val outputFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefs.outputTreeUri = uri.toString()
            outputTreeUri = uri
            outputFolderName = folderNameOf(uri)
            AppLog.i("保存位置已设置为：${outputFolderName ?: uri.lastPathSegment}")
            refreshSavedFiles()
        }
    }

    /** 批量匹配；单条重试也复用这里（目标列表只有一首）。 */
    fun matchSongs(target: List<SongItem>) {
        val outUri = outputTreeUri
        when {
            target.isEmpty() -> notify(context.getString(R.string.need_selection))
            outUri == null -> notify(context.getString(R.string.need_output))
            matching -> Unit
            else -> scope.launch {
                matching = true
                progressDone = 0
                progressTotal = target.size
                val activePlatforms = platformOrder.filter { it in enabledPlatforms }
                if (activePlatforms.isEmpty()) {
                    notify(context.getString(R.string.no_platform_selected))
                    return@launch
                }
                val artistCount = target.sumOf { it.artistList.size }
                AppLog.i(
                    "开始匹配 ${target.size} 首歌曲（含 $artistCount 个歌手条目），来源：${activePlatforms.joinToString("、") { it.displayName }}" +
                        if (overwrite) "，覆盖旧图" else "，保留旧图",
                )
                withContext(Dispatchers.IO) {
                    MatchEngine(context).match(
                        songs = target,
                        orderedPlatforms = activePlatforms,
                        outputTreeUri = outUri,
                        overwrite = overwrite,
                        onSongUpdated = { updated ->
                            songs = songs.map { if (it.id == updated.id) updated else it }
                        },
                        onProgress = { done, total ->
                            progressDone = done
                            progressTotal = total
                        },
                    )
                }
                matching = false
                val ids = target.map { it.id }.toSet()
                val done = songs.count { it.id in ids && it.matchState == MatchState.DONE }
                val skipped = songs.count { it.id in ids && it.matchState == MatchState.EXISTS }
                val partial = songs.count { it.id in ids && it.matchState == MatchState.PARTIAL }
                AppLog.i("匹配结束：新下载 $done 首，已存在 $skipped 首，部分成功 $partial 首")
                refreshSavedFiles()
                checkResult = null
                tab = AppTab.Settings
            }
        }
    }

    /** 按歌手批量匹配：歌手视图的多选下载走这里，一位歌手只处理一次。 */
    fun matchArtists(artists: List<String>) {
        val outUri = outputTreeUri
        when {
            artists.isEmpty() -> notify(context.getString(R.string.need_selection))
            outUri == null -> notify(context.getString(R.string.need_output))
            matching -> Unit
            else -> scope.launch {
                val activePlatforms = platformOrder.filter { it in enabledPlatforms }
                if (activePlatforms.isEmpty()) {
                    notify(context.getString(R.string.no_platform_selected))
                    return@launch
                }
                matching = true
                progressDone = 0
                progressTotal = artists.size
                AppLog.i(
                    "开始按歌手匹配 ${artists.size} 位，来源：${activePlatforms.joinToString("、") { it.displayName }}" +
                        if (overwrite) "，覆盖旧图" else "，保留旧图",
                )
                withContext(Dispatchers.IO) {
                    MatchEngine(context).matchArtists(
                        artists = artists,
                        orderedPlatforms = activePlatforms,
                        outputTreeUri = outUri,
                        overwrite = overwrite,
                        onArtistDone = { outcome ->
                            val key = NameMatcher.normalize(outcome.artist)
                            songs = songs.map { song ->
                                if (song.artistList.none { NameMatcher.normalize(it) == key }) {
                                    song
                                } else {
                                    song.copy(
                                        matchState = when {
                                            outcome.hit == null -> MatchState.NOT_FOUND
                                            outcome.fileName == null -> MatchState.FAILED
                                            outcome.savedNow -> MatchState.DONE
                                            else -> MatchState.EXISTS
                                        },
                                        matchedArtist = outcome.hit?.name ?: song.matchedArtist,
                                        platform = outcome.hit?.platform ?: song.platform,
                                        savedName = outcome.fileName ?: song.savedName,
                                        error = outcome.error,
                                    )
                                }
                            }
                        },
                        onProgress = { done, total ->
                            progressDone = done
                            progressTotal = total
                        },
                    )
                }
                matching = false
                refreshSavedFiles()
                checkResult = null
                AppLog.i("按歌手匹配结束，共处理 ${artists.size} 位")
                tab = AppTab.Settings
            }
        }
    }

    val artistGroups: List<ArtistGroup> = remember(songs, savedFiles) {
        if (songs.isEmpty()) {
            emptyList()
        } else {
            val counts = LinkedHashMap<String, Int>()
            val latest = LinkedHashMap<String, Long>()
            songs.forEach { song ->
                song.artistList.forEach { name ->
                    counts[name] = (counts[name] ?: 0) + 1
                    latest[name] = maxOf(latest[name] ?: 0L, song.addedAt)
                }
            }
            // 排序交给 SongPage（用户可切换），这里只负责聚合
            counts.map { (name, songCount) ->
                val fileName = ImageDownloader.safeFileName(name)
                val uri = savedFiles.uriOf(fileName)
                ArtistGroup(
                    name = name,
                    songCount = songCount,
                    latestAddedAt = latest[name] ?: 0L,
                    savedFileName = if (uri != null) fileName else null,
                    savedUri = uri,
                )
            }
        }
    }

    /** 本地检查：歌曲里的歌手 vs 保存文件夹里的图片。 */
    fun runCheck() {
        when {
            songs.isEmpty() -> notify(context.getString(R.string.need_songs_first))
            checking -> Unit
            else -> scope.launch {
                checking = true
                val result = withContext(Dispatchers.IO) { LibraryChecker.check(songs, savedFiles) }
                checkResult = result
                checking = false
                AppLog.i("本地检查：缺少 ${result.missing.size} 位歌手，多余 ${result.extra.size} 个文件")
            }
        }
    }

    /** 删除选中的多余图片，并用最新的目录内容重算检查结果。 */
    fun deleteExtraImages(files: List<ExtraImageFile>) {
        if (files.isEmpty()) return
        scope.launch {
            val deleted = withContext(Dispatchers.IO) { LibraryChecker.deleteExtras(context, files) }
            val fresh = withContext(Dispatchers.IO) { SavedFiles.load(context, outputTreeUri) }
            savedFiles = fresh
            checkResult = withContext(Dispatchers.IO) { LibraryChecker.check(songs, fresh) }
            AppLog.i("已删除多余图片 $deleted 个")
            notify(context.getString(R.string.deleted_files, deleted))
        }
    }

    /**
     * 单个歌手单独保存后的收尾。
     *
     * 只保存了多歌手中的一位，所以歌曲状态不能直接用这次的结果，
     * 而是用最新的本地文件重新判定（全下完才算已下载，否则是部分下载）。
     */
    fun applySingleArtistSave(target: MatchTarget, outcome: SaveOutcome) {
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { SavedFiles.load(context, outputTreeUri) }
            savedFiles = fresh
            checkResult = null

            target.songId?.let { songId ->
                songs = songs.map { song ->
                    if (song.id != songId) {
                        song
                    } else {
                        val local = downloadStateOf(song.artistList, fresh)
                        song.copy(
                            matchState = when (local) {
                                DownloadState.DOWNLOADED -> MatchState.DONE
                                DownloadState.PARTIAL -> MatchState.PARTIAL
                                else -> song.matchState
                            },
                            matchedArtist = outcome.matchedArtist ?: song.matchedArtist,
                            platform = outcome.platform ?: song.platform,
                            savedName = outcome.savedName ?: song.savedName,
                        )
                    }
                }
            }

            notifySaveOutcome(outcome)
        }
    }

    /** 从任意入口打开某个歌手的匹配面板，保证行为一致。 */
    fun artistTarget(artist: String): MatchTarget = MatchTarget(
        title = artist,
        subtitle = context.getString(R.string.artist_song_count, songs.count { artist in it.artistList }),
        artists = listOf(artist),
        songId = null,
        relatedSongs = songs.filter { artist in it.artistList },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tab) {
                AppTab.Songs -> SongPage(
                    folderName = songFolderName,
                    cachedAt = cachedAt,
                    isScanning = scanning,
                    songs = songs,
                    artistGroups = artistGroups,
                    selectedSongIds = selectedIds,
                    selectedArtistNames = selectedArtists,
                    selectionMode = selectionMode,
                    savedFiles = savedFiles,
                    platformOrder = platformOrder,
                    enabledPlatforms = enabledPlatforms,
                    outputFolderReady = outputFolderName != null,
                    matching = matching,
                    progressDone = progressDone,
                    progressTotal = progressTotal,
                    songSort = songSort,
                    artistSort = artistSort,
                    onSelectSongSort = { value ->
                        songSort = value
                        prefs.songSort = value.id
                    },
                    onSelectArtistSort = { value ->
                        artistSort = value
                        prefs.artistSort = value.id
                    },
                    onToggleSelectionMode = {
                        if (selectionMode) {
                            selectedIds = emptySet()
                            selectedArtists = emptySet()
                        }
                        selectionMode = !selectionMode
                    },
                    onPickFolder = { songFolderPicker.launch(null) },
                    onRescan = { songTreeUri?.let { scanFolder(it, songFolderName) } },
                    onToggleSong = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onToggleArtist = { name ->
                        selectedArtists = if (name in selectedArtists) selectedArtists - name else selectedArtists + name
                    },
                    // 全选只作用于当前可见（可能被搜索过滤过）的条目
                    onSelectAllSongs = { visible -> selectedIds = selectedIds + visible.map { it.id } },
                    onSelectAllArtists = { visible -> selectedArtists = selectedArtists + visible.map { it.name } },
                    onClearSelection = {
                        selectedIds = emptySet()
                        selectedArtists = emptySet()
                    },
                    onSingleMatch = { song ->
                        matchTarget = MatchTarget(
                            title = song.displayTitle,
                            subtitle = song.artistList.joinToString(" / ")
                                .ifBlank { context.getString(R.string.artist_unknown) },
                            artists = song.artistList,
                            songId = song.id,
                        )
                    },
                    onArtistMatch = { group -> matchTarget = artistTarget(group.name) },
                    onTogglePlatform = { togglePlatform(it) },
                    onBatchDownloadSongs = { matchSongs(songs.filter { it.id in selectedIds }) },
                    // 按歌手名字在当前列表里的顺序处理，保持与界面一致的顺序
                    onBatchDownloadArtists = { matchArtists(artistGroups.filter { it.name in selectedArtists }.map { it.name }) },
                )

                AppTab.Settings -> MatchPage(
                    platformOrder = platformOrder,
                    enabledPlatforms = enabledPlatforms,
                    onTogglePlatform = { togglePlatform(it) },
                    onMovePlatform = { platform, delta -> movePlatform(platform, delta) },
                    outputFolderName = outputFolderName,
                    onPickOutput = { outputFolderPicker.launch(null) },
                    overwrite = overwrite,
                    onToggleOverwrite = { value ->
                        overwrite = value
                        prefs.overwriteExisting = value
                    },
                    checkResult = checkResult,
                    checking = checking,
                    onRunCheck = { runCheck() },
                    onDeleteExtras = { files -> deleteExtraImages(files) },
                    onMatchArtist = { artist -> matchTarget = artistTarget(artist) },
                    results = songs.filter { it.matchState != MatchState.IDLE },
                    appVersion = BuildConfig.VERSION_NAME,
                    onRetry = { song -> matchSongs(listOf(song)) },
                    onOpenRepo = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)),
                            )
                        }
                    },
                )
            }
        }
    }

    matchTarget?.let { target ->
        SingleMatchSheet(
            target = target,
            outputTreeUri = outputTreeUri,
            savedFiles = savedFiles,
            overwrite = overwrite,
            onArtistSaved = { savedTarget, outcome -> applySingleArtistSave(savedTarget, outcome) },
            onSaved = { savedTarget, outcome ->
                refreshSavedFiles()
                checkResult = null
                savedTarget.songId?.let { songId ->
                    songs = songs.map { song ->
                        if (song.id != songId) {
                            song
                        } else {
                            song.copy(
                                matchState = outcome.state,
                                matchedArtist = outcome.matchedArtist,
                                platform = outcome.platform,
                                savedName = outcome.savedName,
                                error = outcome.error,
                            )
                        }
                    }
                }
                notifySaveOutcome(outcome)
            },
            onDismiss = { matchTarget = null },
        )
    }

}
