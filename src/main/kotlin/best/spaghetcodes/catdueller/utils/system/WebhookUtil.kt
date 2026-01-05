package best.spaghetcodes.catdueller.utils.system

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Utility object for sending Discord webhook messages with embedded content.
 *
 * Provides builder functions for constructing Discord embed objects and
 * sending them to webhook URLs. All messages are sent with the "Cat Dueller"
 * branding including a custom avatar.
 */
object WebhookUtil {

    /**
     * Sends an embed message to a Discord webhook URL.
     *
     * Constructs a webhook payload with the Cat Dueller branding and sends it
     * via HTTPS POST request. Logs the request body and any errors to stdout.
     *
     * @param url The Discord webhook URL to send the message to.
     * @param embed The embed JSON object to include in the message.
     */
    fun sendEmbed(url: String, embed: JsonObject) {
        val body = JsonObject()
        body.addProperty("content", "")
        body.addProperty("username", "Cat Dueller")
        body.addProperty(
            "avatar_url",
            "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
        )

        val arr = JsonArray()
        arr.add(embed)
        body.add("embeds", arr)

        println("Sending webhook with body...")
        println(body.toString())
        val url = URL(url)
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.addRequestProperty("Content-Type", "application/json")
            conn.addRequestProperty("User-Agent", "Cat-Dueller-Webhook")
            conn.doOutput = true
            conn.requestMethod = "POST"

            DataOutputStream(conn.outputStream).use { it.writeBytes(body.toString()) }
            BufferedReader(InputStreamReader(conn.inputStream)).use { bf ->
                var line: String?
                while (bf.readLine().also { line = it } != null) {
                    println(line)
                }
            }
        } catch (e: Exception) {
            println("Webhook request failed")
            println(conn.responseMessage)
            println(conn.errorStream)
            e.printStackTrace()
        }
    }

    /**
     * Builds a Discord embed JSON object with the specified properties.
     *
     * @param title The embed title displayed at the top.
     * @param description The main text content of the embed. Empty string omits this field.
     * @param fields Array of field objects for structured data display.
     * @param footer Footer object containing text and optional icon.
     * @param author Author object containing name and optional icon.
     * @param thumbnail Thumbnail object containing the image URL.
     * @param color The embed sidebar color as a decimal integer (e.g., 0xFF0000 for red).
     * @return A [JsonObject] representing the complete embed structure.
     */
    fun buildEmbed(
        title: String,
        description: String,
        fields: JsonArray,
        footer: JsonObject,
        author: JsonObject,
        thumbnail: JsonObject,
        color: Int
    ): JsonObject {
        val obj = JsonObject()
        obj.addProperty("title", title)
        if (description != "")
            obj.addProperty("description", description)
        obj.addProperty("color", color)
        obj.add("footer", footer)
        obj.add("author", author)
        obj.add("thumbnail", thumbnail)
        obj.add("fields", fields)
        return obj
    }

    /**
     * Builds a JSON array of embed field objects from a list of field definitions.
     *
     * @param fields List of maps containing "name", "value", and "inline" keys.
     *               The "inline" value should be "true" or "false" as a string.
     * @return A [JsonArray] of field objects for use in an embed.
     */
    fun buildFields(fields: ArrayList<Map<String, String>>): JsonArray {
        val arr = JsonArray()
        for (field in fields) {
            val obj = JsonObject()
            obj.addProperty("name", field["name"])
            obj.addProperty("value", field["value"])
            obj.addProperty("inline", field["inline"] == "true")
            arr.add(obj)
        }
        return arr
    }

    /**
     * Builds an embed author object.
     *
     * @param name The author name to display.
     * @param icon URL of the author's icon image.
     * @return A [JsonObject] representing the author section.
     */
    fun buildAuthor(name: String, icon: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("name", name)
        obj.addProperty("icon_url", icon)
        return obj
    }

    /**
     * Builds an embed thumbnail object.
     *
     * @param url URL of the thumbnail image to display.
     * @return A [JsonObject] representing the thumbnail section.
     */
    fun buildThumbnail(url: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("url", url)
        return obj
    }

    /**
     * Builds an embed footer object.
     *
     * @param text The footer text to display.
     * @param icon URL of the footer icon image.
     * @return A [JsonObject] representing the footer section.
     */
    fun buildFooter(text: String, icon: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("text", text)
        obj.addProperty("icon_url", icon)
        return obj
    }

}
