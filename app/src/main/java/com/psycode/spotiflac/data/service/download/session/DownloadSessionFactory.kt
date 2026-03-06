package com.psycode.spotiflac.data.service.download.session

import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.psycode.spotiflac.data.service.download.core.DownloadConfig
import javax.inject.Inject

class DownloadSessionFactory @Inject constructor() {
    fun create(config: DownloadConfig): SessionManager {
        val settings = SettingsPack()
            .downloadRateLimit(0)
            .uploadRateLimit(DEFAULT_UPLOAD_RATE_LIMIT_BYTES)
            .connectionsLimit(DEFAULT_CONNECTIONS_LIMIT)
            .listenInterfaces(DEFAULT_LISTEN_INTERFACES)
            .activeDownloads((config.maxParallelDownloads * 2).coerceAtLeast(4))
            .activeSeeds(4)
            .activeChecking(2)
            .activeLimit(128)
            .maxPeerlistSize(4_000)
            .alertQueueSize(4_096)
            .validateHttpsTrackers(false)
        settings.setDhtBootstrapNodes(DEFAULT_DHT_BOOTSTRAP)
        settings.setEnableDht(true)
        settings.setEnableLsd(true)

        return SessionManager().apply {
            start(SessionParams(settings))
            listenInterfaces(DEFAULT_LISTEN_INTERFACES)
            if (!isDhtRunning()) {
                runCatching { startDht() }
            }
            maxConnections(DEFAULT_CONNECTIONS_LIMIT)
            maxPeers(DEFAULT_MAX_PEERS)
            maxActiveDownloads((config.maxParallelDownloads * 2).coerceAtLeast(4))
            maxActiveSeeds(4)
        }
    }
}

private const val DEFAULT_UPLOAD_RATE_LIMIT_BYTES = 256 * 1024
private const val DEFAULT_CONNECTIONS_LIMIT = 200
private const val DEFAULT_MAX_PEERS = 200
private const val DEFAULT_LISTEN_INTERFACES = "0.0.0.0:0"
private const val DEFAULT_DHT_BOOTSTRAP =
    "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881"





