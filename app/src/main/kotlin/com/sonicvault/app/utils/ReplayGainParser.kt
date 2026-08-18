/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Reads the ReplayGain track gain (in dB) from local audio files without any
 * third-party tag library. Supports the most common tagged formats:
 *
 *  - MP3  : ID3v2 (TXXX:REPLAYGAIN_TRACK_GAIN, v2.2/v2.3/v2.4)
 *  - FLAC : VORBIS_COMMENT metadata block (replaygain_track_gain)
 *  - OGG  : Vorbis comment header (replaygain_track_gain)
 *
 * Returns null when no usable ReplayGain tag is present (the caller then falls
 * back to the existing loudness-based normalization or no normalization).
 */
object ReplayGainParser {

    private const val MAX_SCAN_BYTES = 1 shl 20 // 1 MiB header scan cap

    /** Returns track gain in dB (e.g. -6.5) or null. */
    fun readTrackGainDb(
        context: Context,
        uri: Uri,
    ): Float? =
        runCatching {
            val stream =
                when (uri.scheme?.lowercase()) {
                    "file" -> uri.path?.let { File(it) }?.takeIf { it.isFile }?.inputStream()
                    "content" -> context.contentResolver.openInputStream(uri)
                    else -> null
                } ?: return null

            stream.use { input ->
                val header = ByteArray(4)
                if (!readFully(input, header)) return null
                when {
                    header.contentEquals("ID3".toByteArray()) -> parseId3v2(input)
                    header.contentEquals("fLaC".toByteArray()) -> parseFlac(input)
                    header.contentEquals("OggS".toByteArray()) -> parseOggVorbis(input, header)
                    else -> null
                }
            }
        }.getOrNull()

    // ---------------------------------------------------------------- ID3v2

    private fun parseId3v2(input: InputStream): Float? {
        // Bytes 3..4 = version, byte 5 = flags, bytes 6..9 = synchsafe size
        val versionBytes = ByteArray(6)
        if (!readFully(input, versionBytes)) return null
        val version = versionBytes[0].toInt() and 0xFF
        val size = readSynchsafe(input, 4) ?: return null
        val frameLimit = (10 + size).coerceAtMost(MAX_SCAN_BYTES)

        var frameIdSize = if (version == 2) 3 else 4
        var position = 10
        while (position < frameLimit) {
            val frameId = ByteArray(frameIdSize)
            if (!readFully(input, frameId)) break
            position += frameIdSize

            val frameSize: Int =
                when (version) {
                    2 -> readInt24(input) ?: break
                    4 -> readSynchsafe(input, 4) ?: break
                    else -> readInt32(input) ?: break
                }
            // 2 bytes of flags
            if (!skipFully(input, 2)) break
            position += 2 + frameSize
            if (frameSize <= 0 || frameSize > frameLimit - position) break

            val frameBody = ByteArray(frameSize)
            if (!readFully(input, frameBody)) break

            val frameName = String(frameId, Charsets.ISO_8859_1)
            if (frameName == "TXXX" || (version == 2 && frameName == "TXX")) {
                parseTxxxBody(frameBody)?.let { return it }
            }
        }
        return null
    }

    private fun parseTxxxBody(body: ByteArray): Float? {
        if (body.isEmpty()) return null
        val encoding = body[0].toInt() and 0xFF
        var offset = 1

        // Read description, terminated per encoding.
        val descriptionEnd = findTerminator(body, offset, encoding) ?: return null
        val description =
            decodeText(
                body,
                offset,
                descriptionEnd,
                encoding,
            ).trim()
        offset = descriptionEnd + terminatorLength(encoding)

        if (!description.equals("replaygain_track_gain", ignoreCase = true)) return null
        if (offset >= body.size) return null

        val value =
            decodeText(
                body,
                offset,
                body.size,
                encoding,
            ).trim()
        return parseGainValue(value)
    }

    // ----------------------------------------------------------------- FLAC

    private fun parseFlac(input: InputStream): Float? {
        // After "fLaC": sequence of metadata blocks: 1 type/flag byte + 3-byte length.
        var position = 4
        while (position < MAX_SCAN_BYTES) {
            val blockHeader = ByteArray(4)
            if (!readFully(input, blockHeader)) return null
            val lastBlock = (blockHeader[0].toInt() and 0x80) != 0
            val blockType = blockHeader[0].toInt() and 0x7F
            val blockLength = readInt24(blockHeader, 1) ?: return null
            position += 4 + blockLength

            if (blockType == 4) { // VORBIS_COMMENT
                val body = ByteArray(blockLength)
                if (!readFully(input, body)) return null
                return parseVorbisCommentBlock(body)
            } else {
                if (!skipFully(input, blockLength)) return null
            }
            if (lastBlock) return null
        }
        return null
    }

    // ------------------------------------------------------------------ OGG

    private fun parseOggVorbis(input: InputStream, firstPageHeader: ByteArray): Float? {
        // Accumulate packets from Ogg pages until we find the vorbis comment
        // header ("\x03vorbis"). The identification header ("\x01vorbis") and
        // comment header are usually in the first 1-2 pages.
        var header = firstPageHeader
        var accumulated = ByteArray(0)
        var scanned = 0

        while (scanned < MAX_SCAN_BYTES) {
            // Page header (first 4 bytes already consumed for the first page).
            // For the first page we have bytes 0..3 ("OggS"); read the rest.
            val restLen = 23 // bytes 4..26 of the 27-byte page header
            val rest = ByteArray(restLen)
            if (header.size < 4) return null
            if (!readFully(input, rest)) return null
            val pageSegments = input.read()
            if (pageSegments < 0) return null
            val segmentTable = ByteArray(pageSegments)
            if (!readFully(input, segmentTable)) return null
            val payloadLen = segmentTable.sumOf { it.toInt() and 0xFF }
            val payload = ByteArray(payloadLen)
            if (!readFully(input, payload)) return null
            scanned += 27 + pageSegments + payloadLen

            accumulated += payload
            // A vorbis comment header packet starts with 0x03 'vorbis'.
            val commentIndex = indexOfPacket(accumulated, "\u0003vorbis")
            if (commentIndex >= 0) {
                val packet = accumulated.copyOfRange(commentIndex, accumulated.size)
                return parseVorbisCommentPacket(packet)
            }

            val continuation = (rest[0].toInt() and 0x01) != 0
            if (!continuation) accumulated = ByteArray(0)
            if (scanned >= MAX_SCAN_BYTES) return null
        }
        return null
    }

    private fun indexOfPacket(data: ByteArray, marker: String): Int {
        val target = marker.toByteArray()
        if (data.size < target.size) return -1
        outer@ for (i in 0..data.size - target.size) {
            for (j in target.indices) {
                if (data[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    // ------------------------------------------------- Vorbis comment format

    private fun parseVorbisCommentBlock(body: ByteArray): Float? {
        // Vorbis comment metadata block: vendor_length(4 LE) vendor, count(4 LE),
        // then count * (length(4 LE) "KEY=value").
        return parseVorbisComments(body, 0)
    }

    private fun parseVorbisCommentPacket(packet: ByteArray): Float? {
        // Packet: 0x03 'vorbis' (7 bytes), then vendor_length(4 LE) ...
        if (packet.size < 7) return null
        return parseVorbisComments(packet, 7)
    }

    private fun parseVorbisComments(data: ByteArray, start: Int): Float? {
        var offset = start
        val vendorLength = readInt32Le(data, offset) ?: return null
        offset += 4
        offset += vendorLength
        if (offset > data.size) return null
        val count = readInt32Le(data, offset) ?: return null
        offset += 4

        repeat(count.coerceAtMost(1024)) {
            val length = readInt32Le(data, offset) ?: return@repeat
            offset += 4
            if (length < 0 || offset + length > data.size) return@repeat
            val comment = String(data, offset, length, Charsets.ISO_8859_1)
            offset += length

            val eq = comment.indexOf('=')
            if (eq > 0 && comment.substring(0, eq).equals("replaygain_track_gain", ignoreCase = true)) {
                return parseGainValue(comment.substring(eq + 1))
            }
        }
        return null
    }

    // ----------------------------------------------------------------- utils

    private fun parseGainValue(raw: String): Float? {
        val normalized = raw.replace(',', '.').trim()
        val match =
            Regex("""([-+]?\d+(?:\.\d+)?)\s*(?:dB)?""", RegexOption.IGNORE_CASE)
                .find(normalized)
                ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun findTerminator(
        data: ByteArray,
        from: Int,
        encoding: Int,
    ): Int? {
        var i = from
        while (i < data.size) {
            if (encoding == 1 || encoding == 2) {
                // UTF-16: look for 00 00 (aligned to 2 bytes)
                if (i + 1 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
                i += 2
            } else {
                if (data[i] == 0.toByte()) return i
                i += 1
            }
        }
        return null
    }

    private fun terminatorLength(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decodeText(
        data: ByteArray,
        from: Int,
        to: Int,
        encoding: Int,
    ): String {
        val slice = data.copyOfRange(from, to)
        return when (encoding) {
            1 -> decodeUtf16(slice, littleEndian = true)
            2 -> decodeUtf16(slice, littleEndian = false)
            else -> String(slice, Charsets.ISO_8859_1)
        }
    }

    private fun decodeUtf16(data: ByteArray, littleEndian: Boolean): String =
        if (data.size >= 2) {
            if (littleEndian) {
                String(data, Charsets.UTF_16LE)
            } else {
                String(data, Charsets.UTF_16BE)
            }
        } else {
            ""
        }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val n = input.read(target, read, target.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Int): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped <= 0) {
                if (input.read() < 0) return false
                remaining -= 1
            } else {
                remaining -= skipped.toInt()
            }
        }
        return true
    }

    private fun readSynchsafe(input: InputStream, count: Int): Int? {
        val bytes = ByteArray(count)
        if (!readFully(input, bytes)) return null
        var value = 0
        for (b in bytes) {
            value = (value shl 7) or (b.toInt() and 0x7F)
        }
        return value
    }

    private fun readInt32(input: InputStream): Int? {
        val bytes = ByteArray(4)
        if (!readFully(input, bytes)) return null
        return readInt32(bytes, 0)
    }

    private fun readInt24(input: InputStream): Int? {
        val bytes = ByteArray(3)
        if (!readFully(input, bytes)) return null
        return readInt24(bytes, 0)
    }

    private fun readInt24(data: ByteArray, offset: Int): Int? {
        if (offset + 3 > data.size) return null
        return (data[offset].toInt() and 0xFF shl 16) or
            (data[offset + 1].toInt() and 0xFF shl 8) or
            (data[offset + 2].toInt() and 0xFF)
    }

    private fun readInt32(data: ByteArray, offset: Int): Int? {
        if (offset + 4 > data.size) return null
        return (data[offset].toInt() and 0xFF shl 24) or
            (data[offset + 1].toInt() and 0xFF shl 16) or
            (data[offset + 2].toInt() and 0xFF shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun readInt32Le(data: ByteArray, offset: Int): Int? {
        if (offset + 4 > data.size) return null
        return (data[offset].toInt() and 0xFF) or
            (data[offset + 1].toInt() and 0xFF shl 8) or
            (data[offset + 2].toInt() and 0xFF shl 16) or
            (data[offset + 3].toInt() and 0xFF shl 24)
    }
}
