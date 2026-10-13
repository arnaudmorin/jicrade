package fr.amorin.jicrade.data.remote

import fr.amorin.jicrade.data.remote.dto.JiraUserDto
import fr.amorin.jicrade.data.remote.dto.TeamDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val listKeys = listOf("platformTeams", "values", "teams", "users", "items", "data")

fun <T> Json.decodeList(element: JsonElement, itemSerializer: KSerializer<T>): List<T> {
    val array: JsonArray = when (element) {
        is JsonArray -> element
        is JsonObject -> listKeys.firstNotNullOfOrNull { element[it] as? JsonArray } ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    return decodeFromJsonElement(ListSerializer(itemSerializer), array)
}

fun Json.decodeTeams(element: JsonElement): List<TeamDto> = decodeList(element, TeamDto.serializer())

fun Json.decodeUsers(element: JsonElement): List<JiraUserDto> = decodeList(element, JiraUserDto.serializer())
