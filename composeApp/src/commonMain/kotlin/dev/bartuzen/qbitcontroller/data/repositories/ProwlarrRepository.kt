package dev.bartuzen.qbitcontroller.data.repositories

import dev.bartuzen.qbitcontroller.data.SettingsManager
import dev.bartuzen.qbitcontroller.model.ProwlarrConfig
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.network.ProwlarrService
import dev.bartuzen.qbitcontroller.network.RequestResult
import dev.bartuzen.qbitcontroller.network.catchRequestError
import dev.bartuzen.qbitcontroller.network.createProwlarrHttpClient
import dev.bartuzen.qbitcontroller.network.platformJsonIo
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.serialization.json.Json

/**
 * Manages the single, global Prowlarr connection (Prowlarr aggregates indexers independently of
 * any particular qBittorrent server, so - unlike [dev.bartuzen.qbitcontroller.data.ServerManager] -
 * there is only one configuration here, not a list). See docs/prowlarr-integration-plan.md, section
 * 4.3 for the reasoning behind reusing [SettingsManager] instead of a dedicated settings namespace.
 */
class ProwlarrRepository(
    private val settingsManager: SettingsManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    val configFlow = settingsManager.prowlarrConfig.flow

    fun getConfig(): ProwlarrConfig = settingsManager.prowlarrConfig.value

    fun setConfig(config: ProwlarrConfig) {
        settingsManager.prowlarrConfig.value = config
    }

    /**
     * Builds a one-off [ProwlarrService] bound to [config]. Not cached, since Prowlarr calls are
     * infrequent (a settings test, or a user-initiated search) compared to qBittorrent's frequent
     * polling - unlike [dev.bartuzen.qbitcontroller.network.RequestManager], there is no need to
     * keep a long-lived client/session around. Also used by
     * [dev.bartuzen.qbitcontroller.data.repositories.search.ProwlarrSearchRepository].
     */
    fun buildService(config: ProwlarrConfig): ProwlarrService {
        val client = createProwlarrHttpClient(config.trustSelfSignedCertificates) {
            install(ContentNegotiation) {
                platformJsonIo(json)
            }

            defaultRequest {
                header("X-Api-Key", config.apiKey)
            }
        }

        return ProwlarrService(client, config.requestUrl)
    }

    suspend fun testConnection(config: ProwlarrConfig): RequestResult<Unit> = catchRequestError(
        block = {
            val service = buildService(config)
            val response = service.getSystemStatus()

            // Prowlarr uses a single X-Api-Key header rather than a username/password pair, so a
            // generic ApiError(code) is more accurate here than RequestError.InvalidCredentials
            // (which is worded around username/password in the UI). A dedicated "invalid API key"
            // error string can be added later if this needs to read better.
            when {
                response.code in 200..<300 && response.body != null -> RequestResult.Success(Unit)
                else -> RequestResult.Error.ApiError(response.code, response.errorMessage)
            }
        },
    )

    /**
     * Used by [dev.bartuzen.qbitcontroller.ui.prowlarr.search.ProwlarrSearchViewModel] to populate
     * the indexer multi-select (see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 2.1).
     * Failure here shouldn't block searching - callers should fall back to an unrestricted search
     * (no indexerIds filter) rather than surfacing this as a hard error.
     */
    suspend fun getIndexers(): RequestResult<List<ProwlarrIndexer>> {
        val config = getConfig()
        if (!config.isConfigured) {
            return RequestResult.Error.RequestError.Unknown("Prowlarr is not configured")
        }

        return catchRequestError(
            block = {
                val service = buildService(config)
                val response = service.getIndexers()
                val indexers = response.body

                if (response.code in 200..<300 && indexers != null) {
                    RequestResult.Success(indexers)
                } else {
                    RequestResult.Error.ApiError(response.code, response.errorMessage)
                }
            },
        )
    }
}
