package com.codetrio.spatialflow.update

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient(private val owner: String, private val repo: String) {

    companion object {
        private const val TAG = "GitHubReleaseClient"
    }

    val latestRelease: ReleaseInfo?
        get() = fetchLatestRelease()

    fun fetchLatestRelease(): ReleaseInfo? {
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SpatialFlow-UpdateChecker")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String? = null
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val tagName = json.getString("tag_name")
                val body = json.optString("body", "")
                val assets = json.getJSONArray("assets")

                val assetNames = mutableMapOf<String, String>()
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    assetNames[asset.getString("name")] = asset.getString("browser_download_url")
                }

                val apkUrl = resolveApkUrl(assetNames)
                val checksumUrl = assetNames["checksum.txt"]

                if (apkUrl != null) {
                    return ReleaseInfo(tagName, body, apkUrl, checksumUrl)
                }
            } else if (responseCode == 403) {
                Log.w(TAG, "GitHub API rate limit hit (403), using web redirect fallback")
                return fetchLatestReleaseViaRedirect()
            } else {
                Log.e(TAG, "GitHub API returned code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch latest release from API, trying fallback", e)
            return fetchLatestReleaseViaRedirect()
        }
        return null
    }

    private fun fetchLatestReleaseViaRedirect(): ReleaseInfo? {
        return try {
            val webUrl = "https://github.com/$owner/$repo/releases/latest"
            val conn = URL(webUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "SpatialFlow-UpdateChecker")

            val location = conn.getHeaderField("Location")
            conn.disconnect()

            if (!location.isNullOrBlank()) {
                val tag = location.substringAfterLast("/")
                if (tag.isNotBlank()) {
                    val supportedAbis = android.os.Build.SUPPORTED_ABIS
                    val primaryAbi = supportedAbis.firstOrNull() ?: "arm64-v8a"
                    val apkUrl = "https://github.com/$owner/$repo/releases/download/$tag/app-$primaryAbi-release.apk"
                    return ReleaseInfo(tag, "New update available ($tag)", apkUrl, null)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fallback release check failed", e)
            null
        }
    }

    private fun resolveApkUrl(assetNames: Map<String, String>): String? {
        val supportedAbis = android.os.Build.SUPPORTED_ABIS

        // 1. Exact ABI match from Gradle splits: app-$abi-release.apk
        for (abi in supportedAbis) {
            val targetName = "app-$abi-release.apk"
            if (assetNames.containsKey(targetName)) {
                return assetNames[targetName]
            }
        }

        // 2. Exact universal or legacy release names
        assetNames["app-universal-release.apk"]?.let { return it }
        assetNames["app-release.apk"]?.let { return it }

        // 3. Flexible ABI match (e.g. SpatialFlow-arm64-v8a.apk)
        for (abi in supportedAbis) {
            val match = assetNames.entries.firstOrNull { (name, _) ->
                name.endsWith(".apk", ignoreCase = true) && name.contains(abi, ignoreCase = true)
            }
            if (match != null) {
                return match.value
            }
        }

        // 4. Universal / release keyword match
        val universal = assetNames.entries.firstOrNull { (name, _) ->
            name.endsWith(".apk", ignoreCase = true) && (name.contains("universal", ignoreCase = true) || name.contains("release", ignoreCase = true))
        }
        if (universal != null) {
            return universal.value
        }

        // 5. Fallback: Any APK asset in the release
        return assetNames.entries.firstOrNull { (name, _) ->
            name.endsWith(".apk", ignoreCase = true)
        }?.value
    }

    fun getAllReleases(): List<ReleaseInfo> {
        val releases = ArrayList<ReleaseInfo>()
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SpatialFlow-UpdateChecker")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String? = null
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonArray = JSONArray(response.toString())
                for (j in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(j)
                    val tagName = json.getString("tag_name")
                    val body = json.optString("body", "")
                    val assets = json.optJSONArray("assets")

                    var apkUrl: String? = null
                    var checksumUrl: String? = null

                    if (assets != null) {
                        val assetNames = mutableMapOf<String, String>()
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            assetNames[asset.getString("name")] = asset.getString("browser_download_url")
                        }

                        apkUrl = resolveApkUrl(assetNames)
                        checksumUrl = assetNames["checksum.txt"]
                    }
                    releases.add(ReleaseInfo(tagName, body, apkUrl, checksumUrl))
                }
            } else {
                Log.e(TAG, "GitHub API returned code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch all releases", e)
        }
        return releases
    }

    class ReleaseInfo(
        @JvmField val tagName: String,
        @JvmField val changelog: String?,
        @JvmField val apkUrl: String?,
        @JvmField val checksumUrl: String?
    )
}
