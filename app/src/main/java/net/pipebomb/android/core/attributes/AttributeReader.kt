package net.pipebomb.android.core.attributes

import net.pipebomb.android.core.model.AttributeMap
import net.pipebomb.android.core.model.AttributeValue
import net.pipebomb.android.core.model.BooleanAttribute
import net.pipebomb.android.core.model.BufferAttribute
import net.pipebomb.android.core.model.DecimalAttribute
import net.pipebomb.android.core.model.IntegerAttribute
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.model.StringAttribute

object AttributeReader {
    fun any(map: AttributeMap?, key: String): AttributeValue? = map?.get(key)

    fun strings(
        map: AttributeMap?,
        key: String,
        formatted: Boolean = true,
        multiple: Boolean = false,
    ): List<String>? {
        val attribute = map?.get(key) as? StringAttribute ?: return null
        if (attribute.values.isEmpty() && attribute.formatted.isEmpty()) return null
        val values = if (formatted && attribute.formatted.isNotEmpty()) {
            attribute.formatted
        } else {
            attribute.values
        }
        return if (multiple) values else values.take(1)
    }

    fun string(
        map: AttributeMap?,
        key: String,
        formatted: Boolean = true,
    ): String? = strings(map, key, formatted, multiple = false)?.firstOrNull()

    fun integers(
        map: AttributeMap?,
        key: String,
        multiple: Boolean = false,
    ): List<Long>? {
        val attribute = map?.get(key) as? IntegerAttribute ?: return null
        if (attribute.values.isEmpty()) return null
        return if (multiple) attribute.values else attribute.values.take(1)
    }

    fun integer(map: AttributeMap?, key: String): Long? =
        integers(map, key, multiple = false)?.firstOrNull()

    fun decimals(
        map: AttributeMap?,
        key: String,
        multiple: Boolean = false,
    ): List<Double>? {
        val attribute = map?.get(key) as? DecimalAttribute ?: return null
        if (attribute.values.isEmpty()) return null
        return if (multiple) attribute.values else attribute.values.take(1)
    }

    fun decimal(map: AttributeMap?, key: String): Double? =
        decimals(map, key, multiple = false)?.firstOrNull()

    fun booleans(
        map: AttributeMap?,
        key: String,
        multiple: Boolean = false,
    ): List<Boolean>? {
        val attribute = map?.get(key) as? BooleanAttribute ?: return null
        if (attribute.values.isEmpty()) return null
        return if (multiple) attribute.values else attribute.values.take(1)
    }

    fun boolean(map: AttributeMap?, key: String): Boolean? =
        booleans(map, key, multiple = false)?.firstOrNull()

    fun resources(
        map: AttributeMap?,
        key: String,
        multiple: Boolean = false,
    ): List<Resource>? {
        val attribute = map?.get(key) as? BufferAttribute ?: return null
        if (attribute.values.isEmpty()) return null
        return if (multiple) attribute.values else attribute.values.take(1)
    }

    fun resource(map: AttributeMap?, key: String): Resource? =
        resources(map, key, multiple = false)?.firstOrNull()
}
