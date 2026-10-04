package com.myremote.app.lg

import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** Optional, untrusted metadata: never follow a redirect or send credentials. */
internal object LgDescription {
    fun location(base: LgDevice, value: String?): URL? = runCatching {
        val uri = URI(value ?: return null)
        if (uri.scheme !in listOf("http", "https") || uri.rawUserInfo != null ||
            uri.host?.removeSurrounding("[", "]") != base.host.removeSurrounding("[", "]")) return null
        uri.toURL()
    }.getOrNull()

    fun read(base: LgDevice, connection: HttpURLConnection, remainingMillis: () -> Long): LgDevice {
        fun checkTime(): Int = remainingMillis().takeIf { it > 0 }?.coerceAtMost(1_200)?.toInt()
            ?: throw IOException("LG discovery ended")
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = checkTime()
            connection.readTimeout = checkTime()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return base
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    connection.readTimeout = checkTime()
                    val count = stream.read(chunk)
                    checkTime()
                    if (count < 0) break
                    if (output.size() + count > 65_536) throw IOException("LG description too large")
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            }
            return parse(base, bytes)
        } finally { connection.disconnect() }
    }

    fun parse(base: LgDevice, bytes: ByteArray): LgDevice {
        require(bytes.size <= 65_536)
        // Android's Harmony factory rejects several JAXP security flags. Reject DTDs before
        // parsing a strictly decoded character stream, independent of parser/provider support.
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
            .toString().removePrefix("\uFEFF")
        require('\u0000' !in text && !Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            "LG description must not contain a DTD"
        }
        val values = mutableMapOf<String, String>()
        val wanted = setOf("friendlyName", "modelNumber", "modelName", "UDN")
        var depth = 0
        var elements = 0
        var field: String? = null
        var fieldDepth = 0
        val content = StringBuilder()
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if (++depth > 32 || ++elements > 1_024) throw SAXException("LG description structure too large")
                val tag = localName?.takeIf(String::isNotEmpty) ?: qName.orEmpty()
                if (field == null && tag in wanted && tag !in values) {
                    field = tag; fieldDepth = depth; content.setLength(0)
                }
            }
            override fun characters(chars: CharArray, start: Int, length: Int) {
                if (field != null && content.length <= 256)
                    content.append(chars, start, length.coerceAtMost(257 - content.length))
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (field != null && depth == fieldDepth) {
                    content.toString().trim().takeIf { it.isNotBlank() && it.length <= 256 }
                        ?.let { values[requireNotNull(field)] = it }
                    field = null
                }
                depth--
            }
        }
        val factory = SAXParserFactory.newInstance().apply {
            isValidating = false
            isNamespaceAware = true
            // Provider flags are defense in depth; strict decoding and the DTD gate are mandatory.
            for (feature in listOf(XMLConstants.FEATURE_SECURE_PROCESSING to true,
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false)) {
                runCatching { setFeature(feature.first, feature.second) }
            }
        }
        factory.newSAXParser().xmlReader.apply {
            contentHandler = handler
            entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External XML resources are forbidden") }
        }.parse(InputSource(StringReader(text)))
        fun value(tag: String): String? = values[tag]
        return base.copy(name = value("friendlyName") ?: base.name,
            model = value("modelNumber") ?: value("modelName"), uuid = base.uuid ?: value("UDN"))
    }
}
