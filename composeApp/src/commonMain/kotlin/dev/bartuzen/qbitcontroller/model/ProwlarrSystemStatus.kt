package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

/**
 * Maps to Prowlarr's `GET /api/v1/system/status` response. Only the fields this app currently
 * needs are declared; everything else is ignored by the JSON decoder (see [dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository]).
 * All fields are nullable with defaults so that minor differences between Prowlarr versions don't
 * break decoding - we mainly rely on the HTTP status code to know whether the connection worked.
 */
@Serializable
data class ProwlarrSystemStatus(
    val appName: String? = null,
    val version: String? = null,
    val instanceName: String? = null,
)
