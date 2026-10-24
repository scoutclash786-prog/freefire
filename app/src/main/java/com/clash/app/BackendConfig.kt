package com.clash.app

/**
 * Address of your Railway backend (see backend/README.md). No secret goes here - the Telegram bot token
 * lives only in Railway variables. Images are saved in Firebase as "tgimg:<imageId>" and shown through
 * BASE_URL/api/images/<imageId>.
 */
object BackendConfig {
    const val BASE_URL = "https://turnament-1-storage-production.up.railway.app"

    fun resolve(v: String): String =
        if (v.startsWith("tgimg:")) BASE_URL.trimEnd('/') + "/api/images/" + v.removePrefix("tgimg:") else v
}
