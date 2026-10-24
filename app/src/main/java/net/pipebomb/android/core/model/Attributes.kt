package net.pipebomb.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class AttributeType(val wireValue: String) {
    String("string"),
    Boolean("boolean"),
    Integer("integer"),
    Decimal("decimal"),
    Buffer("buffer"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String): AttributeType =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

@Serializable
data class Resource(
    val uuid: String = "",
    val url: String = "",
    val extension: String = "",
)

sealed interface AttributeValue {
    val type: AttributeType
    val formatted: List<String>
}

data class StringAttribute(
    val values: List<String>,
    override val formatted: List<String> = emptyList(),
) : AttributeValue {
    override val type: AttributeType = AttributeType.String
}

data class BooleanAttribute(
    val values: List<Boolean>,
    override val formatted: List<String> = emptyList(),
) : AttributeValue {
    override val type: AttributeType = AttributeType.Boolean
}

data class IntegerAttribute(
    val values: List<Long>,
    override val formatted: List<String> = emptyList(),
) : AttributeValue {
    override val type: AttributeType = AttributeType.Integer
}

data class DecimalAttribute(
    val values: List<Double>,
    override val formatted: List<String> = emptyList(),
) : AttributeValue {
    override val type: AttributeType = AttributeType.Decimal
}

data class BufferAttribute(
    val values: List<Resource>,
    override val formatted: List<String> = emptyList(),
) : AttributeValue {
    override val type: AttributeType = AttributeType.Buffer
}

data class UnknownAttribute(
    val wireType: String,
    override val formatted: List<String> = emptyList(),
) : AttributeValue {
    override val type: AttributeType = AttributeType.Unknown
}

@Serializable(with = AttributeMapSerializer::class)
data class AttributeMap(
    val values: Map<String, AttributeValue> = emptyMap(),
) {
    operator fun get(key: String): AttributeValue? = values[key]
}

object AttributeMapSerializer : KSerializer<AttributeMap> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AttributeMap")

    override fun deserialize(decoder: Decoder): AttributeMap {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("AttributeMap requires a JSON decoder")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val values = buildMap {
            for ((key, element) in obj) {
                if (element is JsonNull) continue
                val attributeObject = element as? JsonObject ?: continue
                put(key, parseAttribute(attributeObject))
            }
        }
        return AttributeMap(values)
    }

    override fun serialize(encoder: Encoder, value: AttributeMap) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("AttributeMap requires a JSON encoder")
        val obj = buildJsonObject {
            for ((key, attribute) in value.values) {
                put(key, encodeAttribute(attribute))
            }
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    private fun parseAttribute(obj: JsonObject): AttributeValue {
        val type = AttributeType.fromWire(obj["type"]?.jsonPrimitive?.content ?: "")
        val formatted = (obj["formatted"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        val values = obj["values"] as? JsonArray ?: JsonArray(emptyList())
        return when (type) {
            AttributeType.String -> StringAttribute(
                values = values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                formatted = formatted,
            )

            AttributeType.Boolean -> BooleanAttribute(
                values = values.mapNotNull { (it as? JsonPrimitive)?.booleanOrNull },
                formatted = formatted,
            )

            AttributeType.Integer -> IntegerAttribute(
                values = values.mapNotNull { (it as? JsonPrimitive)?.longOrNull },
                formatted = formatted,
            )

            AttributeType.Decimal -> DecimalAttribute(
                values = values.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull },
                formatted = formatted,
            )

            AttributeType.Buffer -> BufferAttribute(
                values = values.mapNotNull { element ->
                    (element as? JsonObject)?.let { parseResource(it) }
                },
                formatted = formatted,
            )

            AttributeType.Unknown -> UnknownAttribute(
                wireType = obj["type"]?.jsonPrimitive?.content ?: "",
                formatted = formatted,
            )
        }
    }

    private fun parseResource(obj: JsonObject): Resource = Resource(
        uuid = obj["uuid"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        extension = obj["extension"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )

    private fun encodeAttribute(attribute: AttributeValue): JsonObject = buildJsonObject {
        put("type", attribute.type.wireValue)
        put(
            "values",
            buildJsonArray {
                when (attribute) {
                    is StringAttribute -> attribute.values.forEach { add(JsonPrimitive(it)) }
                    is BooleanAttribute -> attribute.values.forEach { add(JsonPrimitive(it)) }
                    is IntegerAttribute -> attribute.values.forEach { add(JsonPrimitive(it)) }
                    is DecimalAttribute -> attribute.values.forEach { add(JsonPrimitive(it)) }
                    is BufferAttribute -> attribute.values.forEach { resource ->
                        add(
                            buildJsonObject {
                                put("uuid", resource.uuid)
                                put("url", resource.url)
                                put("extension", resource.extension)
                            },
                        )
                    }

                    is UnknownAttribute -> Unit
                }
            },
        )
        if (attribute.formatted.isNotEmpty()) {
            put(
                "formatted",
                buildJsonArray {
                    attribute.formatted.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
    }
}
