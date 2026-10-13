package fr.amorin.jicrade.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun mapToJson(value: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)

    @TypeConverter
    fun jsonToMap(value: String): Map<String, String> =
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), value)

    @TypeConverter
    fun respondersToJson(value: List<ResponderRef>): String =
        json.encodeToString(ListSerializer(ResponderRef.serializer()), value)

    @TypeConverter
    fun jsonToResponders(value: String): List<ResponderRef> =
        json.decodeFromString(ListSerializer(ResponderRef.serializer()), value)
}
