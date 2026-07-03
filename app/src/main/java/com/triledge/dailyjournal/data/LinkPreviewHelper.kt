package com.triledge.dailyjournal.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object LinkPreviewHelper {

    suspend fun fetchOgThumbnail(context: Context, urlString: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            
            // Extract og:image
            val ogImageRegex = Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""")
            val twitterImageRegex = Regex("""<meta\s+name=["']twitter:image["']\s+content=["']([^"']+)["']""")
            
            var imageUrl = ogImageRegex.find(html)?.groupValues?.get(1)
                ?: twitterImageRegex.find(html)?.groupValues?.get(1)
            
            if (imageUrl == null) {
                // Fallback to simple favicon or first img tag
                val imgRegex = Regex("""<img\s+[^>]*src=["']([^"']+)["']""")
                imageUrl = imgRegex.find(html)?.groupValues?.get(1)
            }

            if (imageUrl != null) {
                // If it's a relative path, resolve it relative to base URL
                if (imageUrl.startsWith("/")) {
                    val base = "${url.protocol}://${url.host}"
                    imageUrl = base + imageUrl
                } else if (!imageUrl.startsWith("http")) {
                    imageUrl = urlString.removeSuffix("/") + "/" + imageUrl
                }
                
                return@withContext downloadImage(context, imageUrl)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun downloadImage(context: Context, imageUrl: String): String? {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val stickersDir = File(context.filesDir, "link_previews").also { it.mkdirs() }
                val file = File(stickersDir, "thumb_${UUID.randomUUID()}.jpg")
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                return file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
