package com.psycode.spotiflac.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.psycode.spotiflac.data.mappers.toDomain
import com.psycode.spotiflac.data.model.CaptchaCompleteRequestDto
import com.psycode.spotiflac.data.model.ErrorResponse
import com.psycode.spotiflac.data.model.TorrentResultDto
import com.psycode.spotiflac.data.network.TorrentApi
import com.psycode.spotiflac.domain.model.CaptchaRequiredException
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.TorrentResult
import com.psycode.spotiflac.domain.repository.TorrentRepository
import com.turn.ttorrent.bcodec.BDecoder
import com.turn.ttorrent.bcodec.BEValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentRepositoryImpl @Inject constructor(
    private val api: TorrentApi,
    private val gson: Gson,
    @ApplicationContext private val appContext: Context
) : TorrentRepository {

    private data class ParsedCacheEntry(
        val path: String,
        val size: Long,
        val lastModified: Long,
        val files: List<TorrentFile>
    )

    private val parsedCacheByTopic = ConcurrentHashMap<Int, ParsedCacheEntry>()
    private val parseMutexByTopic = ConcurrentHashMap<Int, Mutex>()

    private fun topicMutex(topicId: Int): Mutex =
        parseMutexByTopic.getOrPut(topicId) { Mutex() }

    override fun searchTorrents(
        query: String,
        track: String,
        lossless: Boolean
    ): Flow<List<TorrentResult>> = flow {
        try {
            val dtos: List<TorrentResultDto> = api.searchTorrents(query, lossless, track)
            emit(dtos.map { it.toDomain() })
        } catch (e: HttpException) {
            if (e.code() == 428) {
                val body = e.response()?.errorBody()?.string()
                if (body != null) {
                    val err = gson.fromJson(body, ErrorResponse::class.java)
                    throw CaptchaRequiredException(err.detail.sessionId, err.detail.captchaImage)
                }
            }
            throw e
        }
    }

    override suspend fun completeCaptcha(sessionId: String, solution: String) {
        api.loginComplete(CaptchaCompleteRequestDto(sessionId, solution))
    }

    private suspend fun downloadTorrentFile(topicId: Int): ResponseBody {
        val resp = api.downloadTorrent(topicId)
        if (!resp.isSuccessful || resp.body() == null) {
            throw IOException("Не удалось скачать .torrent: ${resp.code()}")
        }
        return resp.body()!!
    }

    private fun cacheDir(): File =
        File(appContext.filesDir, "torrent_cache").apply { if (!exists()) mkdirs() }

    private fun cacheFile(topicId: Int): File =
        File(cacheDir(), "$topicId.torrent")

    private fun legacyCacheCandidates(topicId: Int): List<File> {
        val legacyDir = File(appContext.cacheDir, "torrents")
        val legacyNamed = appContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith("torrent_${topicId}") && it.name.endsWith(".torrent") }
            .orEmpty()
        return buildList {
            add(File(legacyDir, "t_$topicId.torrent"))
            addAll(legacyNamed)
        }
    }

    private fun ensurePrimaryCache(topicId: Int): File {
        val primary = cacheFile(topicId)
        if (primary.exists() && primary.length() > 0L) return primary

        val legacy = legacyCacheCandidates(topicId).firstOrNull { it.exists() && it.length() > 0L }
            ?: return primary

        runCatching {
            primary.parentFile?.mkdirs()
            legacy.copyTo(primary, overwrite = true)
            Log.d("TorrentRepo", "Cache restored from legacy path: ${legacy.absolutePath}")
        }.onFailure {
            Log.w("TorrentRepo", "Failed to restore legacy cache for topicId=$topicId: ${it.message}")
        }
        return primary
    }

    private fun writeBodyToCache(topicId: Int, body: ResponseBody): File {
        val target = cacheFile(topicId)
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.parentFile?.mkdirs()

        BufferedOutputStream(temp.outputStream(), 64 * 1024).use { out ->
            body.byteStream().use { it.copyTo(out) }
        }

        if (target.exists()) {
            target.delete()
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    override suspend fun getTorrentFiles(topicId: Int): List<TorrentFile> =
        topicMutex(topicId).withLock {
            val tag = "TorrentRepo"
            val utf8 = StandardCharsets.UTF_8
            val cache = ensurePrimaryCache(topicId)

            parsedCacheByTopic[topicId]?.let { entry ->
                if (
                    cache.exists() &&
                    cache.absolutePath == entry.path &&
                    cache.length() == entry.size &&
                    cache.lastModified() == entry.lastModified
                ) {
                    if (Log.isLoggable(tag, Log.DEBUG)) {
                        Log.d(tag, "Parsed cache HIT -> topicId=$topicId files=${entry.files.size}")
                    }
                    return@withLock entry.files
                }
            }

            if (!cache.exists() || cache.length() == 0L) {
                Log.d(tag, "Cache MISS -> downloading .torrent for topicId=$topicId")
                val body = downloadTorrentFile(topicId)
                writeBodyToCache(topicId, body)
                if (Log.isLoggable(tag, Log.DEBUG)) {
                    Log.d(tag, "Downloaded & cached: ${cache.absolutePath} (${cache.length()} bytes)")
                }
            } else {
                if (Log.isLoggable(tag, Log.DEBUG)) {
                    Log.d(tag, "Cache HIT -> using ${cache.absolutePath} (${cache.length()} bytes)")
                }
            }

            fun bbToString(bb: ByteBuffer): String {
                val dup = bb.duplicate()
                val arr = ByteArray(dup.remaining())
                dup.get(arr)
                return String(arr, utf8)
            }

            fun anyToString(v: Any?): String = when (v) {
                is String -> v
                is ByteArray -> String(v, utf8)
                is ByteBuffer -> bbToString(v)
                is BEValue -> anyToString(v.value)
                else -> v?.toString() ?: ""
            }

            fun anyToLong(v: Any?): Long = when (v) {
                is Long -> v
                is BigInteger -> v.toLong()
                is Number -> v.toLong()
                is BEValue -> anyToLong(v.value)
                else -> error("Unexpected number type: ${v?.javaClass}")
            }

            fun beGet(map: Map<*, *>, key: String): BEValue? {
                for ((k, v) in map) {
                    val ks = when (k) {
                        is String -> k
                        is ByteArray -> String(k, utf8)
                        is ByteBuffer -> bbToString(k)
                        else -> continue
                    }
                    if (ks == key) return v as BEValue
                }
                return null
            }

            fun parseFast(file: File): List<TorrentFile> {
                BufferedInputStream(FileInputStream(file), 64 * 1024).use { bis ->
                    val top = BDecoder.bdecode(bis).value as Map<*, *>
                    val infoBe = beGet(top, "info")
                        ?: throw IllegalArgumentException("Invalid torrent: missing \"info\"")
                    val infoMap = infoBe.value as Map<*, *>

                    val filesBe = (beGet(infoMap, "files")?.value as? List<*>)
                        ?.mapNotNull { it as? BEValue }
                    if (filesBe != null) {
                        val result = ArrayList<TorrentFile>(filesBe.size)
                        for (fileBe in filesBe) {
                            val fMap = fileBe.value as Map<*, *>
                            val size = anyToLong(beGet(fMap, "length")!!.value)
                            @Suppress("UNCHECKED_CAST")
                            val segs = beGet(fMap, "path")!!.value as List<BEValue>

                            val sb = StringBuilder()
                            for (i in segs.indices) {
                                if (i > 0) sb.append('/')
                                sb.append(anyToString(segs[i].value))
                            }
                            val innerPath = sb.toString()
                            val name = innerPath.substringAfterLast('/')

                            result += TorrentFile(
                                name = name,
                                size = size,
                                torrentFilePath = file.absolutePath,
                                innerPath = innerPath
                            )
                        }
                        return result
                    }

                    val name = anyToString(beGet(infoMap, "name")!!.value)
                    val size = anyToLong(beGet(infoMap, "length")!!.value)
                    return listOf(
                        TorrentFile(
                            name = name,
                            size = size,
                            torrentFilePath = file.absolutePath,
                            innerPath = name
                        )
                    )
                }
            }

            try {
                val list = parseFast(cache)
                parsedCacheByTopic[topicId] = ParsedCacheEntry(
                    path = cache.absolutePath,
                    size = cache.length(),
                    lastModified = cache.lastModified(),
                    files = list
                )
                Log.d(tag, "Parsed OK from cache: topicId=$topicId, files=${list.size}")
                list
            } catch (e: Exception) {
                Log.w(tag, "Cache parse error (topicId=$topicId): ${e.message} -> redownloading")
                cache.delete()
                val body = downloadTorrentFile(topicId)
                writeBodyToCache(topicId, body)
                if (Log.isLoggable(tag, Log.DEBUG)) {
                    Log.d(tag, "Re-downloaded cache for topicId=$topicId -> ${cache.absolutePath} (${cache.length()} bytes)")
                }
                val list = parseFast(cache)
                parsedCacheByTopic[topicId] = ParsedCacheEntry(
                    path = cache.absolutePath,
                    size = cache.length(),
                    lastModified = cache.lastModified(),
                    files = list
                )
                Log.d(tag, "Parsed OK after re-download: topicId=$topicId, files=${list.size}")
                list
            }
        }
}
