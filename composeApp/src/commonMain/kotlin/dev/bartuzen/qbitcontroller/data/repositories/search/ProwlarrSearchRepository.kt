package dev.bartuzen.qbitcontroller.data.repositories.search

import dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.model.toSearchResult
import dev.bartuzen.qbitcontroller.network.RequestResult
import dev.bartuzen.qbitcontroller.network.catchRequestError

/**
 * Runs a one-shot search against Prowlarr and adapts the response onto the app's existing
 * [Search.Result] model (see [dev.bartuzen.qbitcontroller.model.toSearchResult]) so it can reuse
 * the qBittorrent-search-plugin result UI. Unlike [SearchResultRepository], there is no
 * start/poll/stop lifecycle here - Prowlarr's `/api/v1/search` returns everything in one response.
 *
 * Round 2 of docs/prowlarr-integration-plan.md: this is not wired into any ViewModel/UI yet.
 */
class ProwlarrSearchRepository(
    private val prowlarrRepository: ProwlarrRepository,
) {
    suspend fun search(query: String, indexerIds: List<Int>? = null): RequestResult<List<Search.Result>> {
        val config = prowlarrRepository.getConfig()
        if (!config.isConfigured) {
            return RequestResult.Error.RequestError.Unknown("Prowlarr is not configured")
        }

        return catchRequestError(
            block = {
                val service = prowlarrRepository.buildService(config)
                val response = service.search(query, indexerIds)
                val results = response.body

                if (response.code in 200..<300 && results != null) {
                    val torrentResults = results
                        .asSequence()
                        // qBittorrent can't act on usenet releases, only torrents. protocol is
                        // treated as "torrent" when absent, since some indexers may not fill it in.
                        .filter { it.protocol == null || it.protocol.equals("torrent", ignoreCase = true) }
                        .map { it.toSearchResult() }
                        // Drop anything Prowlarr didn't give us a downloadUrl/magnetUrl/infoUrl
                        // for - there would be nothing to hand to AddTorrentScreen.
                        .filter { it.fileUrl.isNotBlank() }
                        .toList()

                    RequestResult.Success(torrentResults)
                } else {
                    RequestResult.Error.ApiError(response.code)
                }
            },
        )
    }
}
