package dev.bartuzen.qbitcontroller.data.repositories.search

import dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.model.toSearchResult
import dev.bartuzen.qbitcontroller.network.RequestResult
import dev.bartuzen.qbitcontroller.network.catchRequestError

/**
 * Runs a one-shot search against Prowlarr and adapts the response onto the app's existing
 * [Search.Result] model (see [dev.bartuzen.qbitcontroller.model.toSearchResult]) so it can reuse
 * the same result-list shape as the qBittorrent-search-plugin feature (though round 3's
 * ProwlarrSearchScreen has its own, separate UI - see docs/prowlarr-integration-plan.md). Unlike
 * SearchResultRepository, there is no start/poll/stop lifecycle here - Prowlarr's
 * `/api/v1/search` returns everything in one response.
 */
class ProwlarrSearchRepository(
    private val prowlarrRepository: ProwlarrRepository,
) {
    suspend fun search(
        query: String,
        indexerIds: List<Int>? = null,
        categories: List<Int>? = null,
    ): RequestResult<List<Search.Result>> {
        val config = prowlarrRepository.getConfig()
        if (!config.isConfigured) {
            return RequestResult.Error.RequestError.Unknown("Prowlarr is not configured")
        }

        return catchRequestError(
            block = {
                val service = prowlarrRepository.buildService(config)
                val response = service.search(query, indexerIds, categories)
                val results = response.body

                if (response.code in 200..<300 && results != null) {
                    val torrentResults = results
                        .asSequence()
                        // qBittorrent can't act on usenet releases, only torrents. protocol is
                        // treated as "torrent" when absent, since some indexers may not fill it in.
                        .filter { it.protocol == null || it.protocol.equals("torrent", ignoreCase = true) }
                        .map { it.toSearchResult() }
                        // Drop anything Prowlarr didn't give us a downloadUrl/magnetUrl for -
                        // there would be nothing to hand to qBittorrent.
                        .filter { it.fileUrl.isNotBlank() }
                        .toList()

                    RequestResult.Success(torrentResults)
                } else {
                    RequestResult.Error.ApiError(response.code)
                }
            },
        )
    }

    /**
     * Downloads a .torrent file's raw bytes directly from the client, so that they can be handed
     * to [dev.bartuzen.qbitcontroller.data.repositories.AddTorrentRepository.addTorrent] as a file
     * upload instead of a URL. This is the client-side alternative to relying on the qBittorrent
     * server being able to reach Prowlarr on its own (see docs/prowlarr-integration-plan.md,
     * section 4.7) - since this device already has a working connection to Prowlarr (it just
     * searched it), it can fetch the file itself and forward the bytes to qBittorrent, which only
     * needs to be reachable from this device, not from Prowlarr.
     *
     * Not used for magnet links - those are short strings that qBittorrent resolves on its own via
     * trackers/DHT, so they are passed through as a link instead of being "downloaded" here.
     */
    suspend fun downloadTorrentFile(url: String): RequestResult<ByteArray> {
        val config = prowlarrRepository.getConfig()
        if (!config.isConfigured) {
            return RequestResult.Error.RequestError.Unknown("Prowlarr is not configured")
        }

        return catchRequestError(
            block = {
                val service = prowlarrRepository.buildService(config)
                val response = service.downloadFile(url)

                if (response.code in 200..<300 && response.body != null) {
                    RequestResult.Success(response.body)
                } else {
                    RequestResult.Error.ApiError(response.code)
                }
            },
        )
    }
}
