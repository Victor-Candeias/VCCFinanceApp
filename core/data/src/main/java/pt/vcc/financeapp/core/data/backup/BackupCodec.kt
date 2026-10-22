package pt.vcc.financeapp.core.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pt.vcc.financeapp.core.database.backup.BackupTable
import pt.vcc.financeapp.core.database.backup.DatabaseSnapshot

internal data class BackupPayload(val createdAt: Instant, val snapshot: DatabaseSnapshot)

/** Conteúdo (antes de cifrar) do backup: JSON comprimido com GZIP. */
internal object BackupCodec {
    const val FORMAT = "vccfinanceapp-backup"
    const val VERSION = 1

    /** Proteção contra ficheiros que se expandem desmesuradamente ao descomprimir. */
    private const val MAX_PLAIN_BYTES = 256L * 1024 * 1024
    private const val BLOB = "base64"

    fun encode(payload: BackupPayload): ByteArray {
        val json = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("schemaVersion", payload.snapshot.schemaVersion)
            put("createdAt", payload.createdAt.toString())
            putJsonArray("tables") {
                payload.snapshot.tables.forEach { table ->
                    add(
                        buildJsonObject {
                            put("name", table.name)
                            put("columns", JsonArray(table.columns.map(::JsonPrimitive)))
                            put("rows", JsonArray(table.rows.map { row -> JsonArray(row.map(::encodeValue)) }))
                        },
                    )
                }
            }
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): BackupPayload {
        val text = try {
            inflate(bytes).toString(Charsets.UTF_8)
        } catch (e: IOException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        }
        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            if (root.string("format") != FORMAT) throw BackupException(BackupError.NOT_A_BACKUP)
            if (root.getValue("version").jsonPrimitive.int > VERSION) throw BackupException(BackupError.UNSUPPORTED_VERSION)
            val tables = root.getValue("tables").jsonArray.map { element ->
                val table = element.jsonObject
                val columns = table.getValue("columns").jsonArray.map { it.jsonPrimitive.content }
                val rows = table.getValue("rows").jsonArray.map { row ->
                    row.jsonArray.map(::decodeValue).also {
                        if (it.size != columns.size) throw BackupException(BackupError.NOT_A_BACKUP)
                    }
                }
                BackupTable(table.string("name"), columns, rows)
            }
            BackupPayload(
                createdAt = Instant.parse(root.string("createdAt")),
                snapshot = DatabaseSnapshot(root.getValue("schemaVersion").jsonPrimitive.int, tables),
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: SerializationException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        } catch (e: NoSuchElementException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        } catch (e: java.time.format.DateTimeParseException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        }
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun encodeValue(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is ByteArray -> buildJsonObject { put(BLOB, Base64.getEncoder().encodeToString(value)) }
        else -> throw IllegalArgumentException("Tipo não suportado: ${value::class.simpleName}")
    }

    private fun decodeValue(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content.any { it == '.' || it == 'e' || it == 'E' } -> element.content.toDouble()
            else -> element.content.toLongOrNull() ?: throw BackupException(BackupError.NOT_A_BACKUP)
        }
        is JsonObject -> Base64.getDecoder().decode(element.getValue(BLOB).jsonPrimitive.content)
        is JsonArray -> throw BackupException(BackupError.NOT_A_BACKUP)
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_PLAIN_BYTES) throw BackupException(BackupError.TOO_LARGE)
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }
}
