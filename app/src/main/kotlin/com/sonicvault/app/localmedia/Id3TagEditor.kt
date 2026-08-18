/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.localmedia

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Minimal ID3v2.3 tag reader/editor for local audio files.
 *
 * Reads existing tags (v2.2/v2.3/v2.4), edits the standard text frames in
 * place, and writes back as ID3v2.3 while preserving the original tag size so
 * the audio data never needs to move. Files without an existing tag get a new
 * v2.3 tag prepended.
 */
class Id3TagEditor private constructor(
    private val file: RandomAccessFile,
    private val tagStartOffset: Long,
    private val tagSize: Int,
    private val frames: MutableMap<String, Frame>,
    private val paddingSize: Int,
) : AutoCloseable {
    data class TagData(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: String? = null,
        val track: String? = null,
    )

    private data class Frame(
        val id: String,
        val payload: ByteArray,
        val flags: ByteArray,
    )

    private fun readFramePayload(id: String): String? {
        val frame = frames[id] ?: return null
        // Skip encoding byte; assume ISO-8859-1 for byte reading simplicity.
        return String(frame.payload, 1, frame.payload.size - 1, Charsets.ISO_8859_1)
            .substringBefore('\u0000')
            .takeIf { it.isNotBlank() }
    }

    fun read(): TagData =
        TagData(
            title = readFramePayload("TIT2"),
            artist = readFramePayload("TPE1"),
            album = readFramePayload("TALB"),
            year = readFramePayload("TDRC") ?: readFramePayload("TYER"),
            track = readFramePayload("TRCK"),
        )

    /**
     * Writes the given non-null fields back to the file. Unknown fields keep
     * their existing values. The tag is rewritten in place (same total size).
     */
    fun write(update: TagData) {
        val textFrames =
            mapOf(
                "TIT2" to update.title,
                "TPE1" to update.artist,
                "TALB" to update.album,
                "TDRC" to update.year,
                "TRCK" to update.track,
            )
        for ((id, value) in textFrames) {
            if (value == null) continue
            frames[id] =
                Frame(
                    id = id,
                    payload = byteArrayOf(0x00) + value.toByteArray(Charsets.ISO_8859_1),
                    flags = ByteArray(2),
                )
        }

        val sortedFrames = frames.values.sortedBy { it.id }
        var frameBytesSize = 0
        for (frame in sortedFrames) {
            frameBytesSize += 4 + 4 + 2 + frame.payload.size
        }
        val frameBytes = ByteArray(frameBytesSize)
        var frameOffset = 0
        for (frame in sortedFrames) {
            val idBytes = frame.id.toByteArray(Charsets.ISO_8859_1)
            val sizeBytes = intToSynchsafe(frame.payload.size)
            System.arraycopy(idBytes, 0, frameBytes, frameOffset, 4)
            System.arraycopy(sizeBytes, 0, frameBytes, frameOffset + 4, 4)
            System.arraycopy(frame.flags, 0, frameBytes, frameOffset + 8, 2)
            System.arraycopy(frame.payload, 0, frameBytes, frameOffset + 10, frame.payload.size)
            frameOffset += 10 + frame.payload.size
        }

        val headerSize = 10
        val newTagSize = headerSize + frameBytes.size + paddingSize
        // Preserve original tag size; grow if needed (rare, and keeps audio offset stable when unchanged).
        val totalSize = maxOf(newTagSize, headerSize + tagSize.coerceAtLeast(0))

        val output = ByteArray(totalSize)
        // v2.3 header
        output[0] = 'I'.code.toByte()
        output[1] = 'D'.code.toByte()
        output[2] = '3'.code.toByte()
        output[3] = 3 // version major
        output[4] = 0 // revision
        output[5] = 0 // flags
        val synchsafeTotal = intToSynchsafe(totalSize - headerSize)
        System.arraycopy(synchsafeTotal, 0, output, 6, 4)
        System.arraycopy(frameBytes, 0, output, headerSize, frameBytes.size)

        file.seek(tagStartOffset)
        file.write(output, 0, totalSize)
        file.fd.sync()
    }

    override fun close() {
        file.close()
    }

    companion object {
        /** Opens [uri] (file:// or content://) for tag editing. Returns null if no editable file. */
        fun open(
            context: Context,
            uri: Uri,
        ): Id3TagEditor? {
            val path =
                when (uri.scheme?.lowercase()) {
                    "file" -> uri.path
                    "content" -> resolveContentPath(context, uri)
                    else -> null
                }
            if (path == null) return null
            val file = runCatching { RandomAccessFile(path, "rw") }.getOrNull() ?: return null
            return runCatching { parse(context, file) }.getOrNull()
        }

        private fun resolveContentPath(
            context: Context,
            uri: Uri,
        ): String? =
            runCatching {
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(MediaStore.Audio.Media.DATA),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val column = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                            if (column >= 0) cursor.getString(column) else null
                        } else {
                            null
                        }
                    }
            }.getOrNull()

        private fun parse(
            context: Context,
            file: RandomAccessFile,
        ): Id3TagEditor {
            val length = file.length()
            if (length < 10) {
                // No room for a tag — create one at the start of the file.
                return Id3TagEditor(file, 0L, 0, mutableMapOf(), 0)
            }
            val header = ByteArray(10)
            file.seek(0)
            file.readFully(header)
            val isId3 =
                header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
            if (!isId3) {
                // No existing tag — write a new one at the start with generous padding.
                return Id3TagEditor(file, 0L, 0, mutableMapOf(), 2048)
            }
            val versionMajor = header[3].toInt() and 0xFF
            val hasFooter = (header[5].toInt() and 0x10) != 0
            val tagBodySize = readSynchsafe(header, 6)
            val frameHeaderSize = if (versionMajor == 2) 6 else 10
            val sizeFieldWidth = if (versionMajor == 2) 3 else 4

            val totalTagSize = tagBodySize + 10 + (if (hasFooter) 10 else 0)
            if (totalTagSize > length) {
                return Id3TagEditor(file, 0L, 0, mutableMapOf(), 2048)
            }

            val body = ByteArray(tagBodySize)
            file.seek(10)
            file.readFully(body)

            val frames = mutableMapOf<String, Frame>()
            var offset = 0
            var paddingSize = 0
            while (offset + frameHeaderSize <= body.size) {
                val frameIdBytes = body.copyOfRange(offset, offset + if (versionMajor == 2) 3 else 4)
                val frameId = String(frameIdBytes, Charsets.ISO_8859_1)
                if (frameId == "\u0000\u0000\u0000" || frameId == "\u0000\u0000\u0000\u0000") {
                    paddingSize = body.size - offset
                    break
                }
                if (frameId.startsWith("\u0000")) {
                    paddingSize = body.size - offset
                    break
                }
                val sizeBytes = body.copyOfRange(offset + if (versionMajor == 2) 3 else 4, offset + frameHeaderSize)
                val frameSize =
                    if (versionMajor == 2) {
                        (sizeBytes[0].toInt() and 0xFF) shl 16 or ((sizeBytes[1].toInt() and 0xFF) shl 8) or (sizeBytes[2].toInt() and 0xFF)
                    } else {
                        readSynchsafe(sizeBytes, 0)
                    }
                val flags =
                    if (versionMajor == 2) {
                        ByteArray(0)
                    } else {
                        body.copyOfRange(offset + 4, offset + 6)
                    }
                val payloadStart = offset + frameHeaderSize
                val payloadEnd = (payloadStart + frameSize).coerceAtMost(body.size)
                if (payloadStart > body.size) break
                frames[frameId] =
                    Frame(
                        id = frameId,
                        payload = body.copyOfRange(payloadStart, payloadEnd),
                        flags = flags,
                    )
                offset = payloadEnd
            }

            return Id3TagEditor(
                file = file,
                tagStartOffset = 0L,
                tagSize = totalTagSize,
                frames = frames,
                paddingSize = paddingSize,
            )
        }

        private fun readSynchsafe(
            bytes: ByteArray,
            offset: Int,
        ): Int =
            ((bytes[offset].toInt() and 0x7F) shl 21) or
                ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
                ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
                (bytes[offset + 3].toInt() and 0x7F)

        private fun intToSynchsafe(value: Int): ByteArray =
            byteArrayOf(
                ((value ushr 21) and 0x7F).toByte(),
                ((value ushr 14) and 0x7F).toByte(),
                ((value ushr 7) and 0x7F).toByte(),
                (value and 0x7F).toByte(),
            )
    }
}

/** Convenience helper: read then write tag fields for a local song's uri. */
fun editId3Tags(
    context: Context,
    uri: Uri,
    update: Id3TagEditor.TagData,
): Result<Id3TagEditor.TagData> =
    runCatching {
        val editor = Id3TagEditor.open(context, uri)
            ?: throw IOException("Unable to open file for tag editing")
        editor.use { it ->
            val current = it.read()
            it.write(update)
            current
        }
    }
