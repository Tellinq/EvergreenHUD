package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.polyfrost.evergreenhud.client.hud.potion.OverrideCondition.Type
import org.polyfrost.oneconfig.internal.ui.components.effect.normalizeEffectIds
import org.polyfrost.oneconfig.utils.v1.JsonUtils
import org.slf4j.LoggerFactory

const val CATEGORY_BENEFICIAL = "beneficial"
const val CATEGORY_NEUTRAL = "neutral"
const val CATEGORY_HARMFUL = "harmful"
const val AMBIENT = "ambient"
const val NON_AMBIENT = "non_ambient"
const val PERMANENT = "permanent"
const val FINITE = "finite"

private val AMBIENT_VALUES = listOf(AMBIENT, NON_AMBIENT)
private val DURATION_VALUES = listOf(PERMANENT, FINITE)

private const val FORMAT_VERSION = 2

private var nextUid = 0

/** [id] is the namespaced registry ID such as `minecraft:speed` */
class EffectFacts(val id: String?, val category: String, val ambient: Boolean, val permanent: Boolean = false)

enum class Match(val key: String, val title: String) {
    ANY("any", "Any rule"),
    NONE("none", "No rule");

    companion object {
        fun of(key: String?): Match = entries.firstOrNull { it.key == key } ?: ANY
    }
}

class OverrideCondition(
    val type: Type,
    values: List<String> = emptyList(),
    negated: Boolean = false,
) {
    var values by mutableStateOf(values)
    var negated by mutableStateOf(negated)

    init {
        type.choices?.let { choices ->
            val picked = if (this.negated) choices - this.values.toSet() else this.values
            this.values = listOf(picked.singleOrNull() ?: choices.first())
            this.negated = false
        }
    }

    /** [choices] is set for types that are a single pick between two values, so they never negate */
    enum class Type(val key: String, val title: String, val choices: List<String>? = null) {
        EFFECT("effect", "Effect"),
        CATEGORY("category", "Category"),
        AMBIENT("ambient", "Ambient", AMBIENT_VALUES),
        DURATION("duration", "Duration", DURATION_VALUES);

        companion object {
            fun of(key: String?): Type? = entries.firstOrNull { it.key == key }
        }
    }

    val isActive: Boolean get() = values.isNotEmpty()

    fun test(facts: EffectFacts): Boolean {
        val hit = when (type) {
            Type.EFFECT -> facts.id in values
            Type.CATEGORY -> facts.category in values
            Type.AMBIENT -> (if (facts.ambient) AMBIENT else NON_AMBIENT) in values
            Type.DURATION -> (if (facts.permanent) PERMANENT else FINITE) in values
        }
        return hit != negated
    }

    fun copy(): OverrideCondition = OverrideCondition(type, values, negated)
}

class OverrideRule(conditions: List<OverrideCondition> = emptyList()) {
    val conditions: MutableList<OverrideCondition> = mutableStateListOf<OverrideCondition>().apply { addAll(conditions) }

    val uid: Int = nextUid++

    val isActive: Boolean get() = conditions.any { it.isActive }

    fun matches(facts: EffectFacts): Boolean = conditions.filter { it.isActive }.all { it.test(facts) }

    /**
     * One condition of each type, and no Category beside an "Effect is", as within a rule those only repeat or
     * contradict each other. "Effect is not" stays allowed beside a Category, as in "beneficial except Speed"
     */
    fun canAdd(type: Type): Boolean = when {
        conditions.any { it.type == type } -> false
        type == Type.CATEGORY -> conditions.none { it.type == Type.EFFECT && !it.negated }
        else -> true
    }

    val locksEffectNegation: Boolean get() = conditions.any { it.type == Type.CATEGORY }

    fun add(type: Type) {
        if (!canAdd(type)) return
        conditions += OverrideCondition(type, negated = type == Type.EFFECT && locksEffectNegation)
    }

    fun copy(): OverrideRule = OverrideRule(conditions.map { it.copy() })
}

class OverrideGroup(
    name: String = "New Group",
    enabled: Boolean = true,
    match: Match = Match.ANY,
    rules: List<OverrideRule> = emptyList(),
    val settings: EffectComponentSettings = EffectComponentSettings(),
) {
    var name by mutableStateOf(name)
    var enabled by mutableStateOf(enabled)
    var match by mutableStateOf(match)
    val rules: MutableList<OverrideRule> = mutableStateListOf<OverrideRule>().apply { addAll(rules) }

    val uid: Int = nextUid++

    fun matches(facts: EffectFacts): Boolean {
        if (!enabled) return false
        val active = rules.filter { it.isActive }
        if (active.isEmpty()) return false
        return when (match) {
            Match.ANY -> active.any { it.matches(facts) }
            Match.NONE -> active.none { it.matches(facts) }
        }
    }

    fun copy(name: String = this.name): OverrideGroup = OverrideGroup(
        name,
        enabled,
        match,
        rules.map { it.copy() },
        EffectComponentSettings().also { it.copyFrom(settings) },
    )
}

class OverrideGroupsModel {
    var groups: MutableList<OverrideGroup> = mutableStateListOf()
        private set

    var revision by mutableStateOf(0)
        private set

    var persist: (String) -> Unit = {}

    private var encoded = ""

    fun sync(data: String) {
        if (data === encoded) return
        if (data != encoded) {
            groups = mutableStateListOf<OverrideGroup>().apply { addAll(decode(data)) }
            revision++
        }
        encoded = data
    }

    fun replace(next: List<OverrideGroup>) {
        groups = mutableStateListOf<OverrideGroup>().apply { addAll(next) }
        changed()
    }

    fun changed() {
        encoded = encode(groups)
        revision++
        persist(encoded)
    }

    fun deepCopy(): OverrideGroupsModel = OverrideGroupsModel().also { it.sync(encode(groups)) }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Potion Effects")

        fun encode(groups: List<OverrideGroup>): String {
            if (groups.isEmpty()) return ""
            val root = JsonObject()
            root.addProperty("version", FORMAT_VERSION)
            root.add("groups", JsonArray().also { array ->
                for (group in groups) array.add(JsonObject().also { json ->
                    json.addProperty("name", group.name)
                    json.addProperty("enabled", group.enabled)
                    json.addProperty("match", group.match.key)
                    json.add("rules", JsonArray().also { rules ->
                        for (rule in group.rules) rules.add(JsonObject().also { r -> r.add("conditions", encodeConditions(rule.conditions)) })
                    })
                    json.add("settings", group.settings.toJson())
                })
            })
            return root.toString()
        }

        fun decode(data: String): MutableList<OverrideGroup> {
            if (data.isBlank()) return mutableListOf()
            return try {
                val root = JsonUtils.parse(data).asJsonObject
                root.getAsJsonArray("groups")?.mapTo(mutableListOf()) { element ->
                    val json = element.asJsonObject
                    val matchKey = json.get("match")?.asString
                    val rules = json.getAsJsonArray("rules")
                        ?.map { OverrideRule(decodeConditions(it.asJsonObject.getAsJsonArray("conditions"))) }
                        ?: legacyRules(matchKey, decodeConditions(json.getAsJsonArray("conditions")))
                    OverrideGroup(
                        name = json.get("name")?.asString ?: "Group",
                        enabled = json.get("enabled")?.asBoolean ?: true,
                        match = Match.of(matchKey),
                        rules = rules.filter { it.conditions.isNotEmpty() },
                        settings = EffectComponentSettings().also { s -> json.getAsJsonObject("settings")?.let(s::readJson) },
                    )
                } ?: mutableListOf()
            } catch (e: Exception) {
                LOGGER.error("Failed to read the potion effect override groups, they will be reset", e)
                mutableListOf()
            }
        }

        private fun legacyRules(match: String?, conditions: List<OverrideCondition>): List<OverrideRule> =
            if (match == "all" || match == null) listOf(OverrideRule(conditions))
            else conditions.map { OverrideRule(listOf(it)) }

        private fun encodeConditions(conditions: List<OverrideCondition>): JsonArray = JsonArray().also { array ->
            for (condition in conditions) array.add(JsonObject().also { c ->
                c.addProperty("type", condition.type.key)
                c.addProperty("negated", condition.negated)
                c.add("values", JsonArray().also { values -> condition.values.forEach { values.add(JsonPrimitive(it)) } })
            })
        }

        private fun decodeConditions(array: JsonArray?): List<OverrideCondition> = array?.mapNotNull { c ->
            val condition = c.asJsonObject
            val type = Type.of(condition.get("type")?.asString) ?: return@mapNotNull null
            val values = condition.getAsJsonArray("values")?.map { it.asString }.orEmpty()
            OverrideCondition(
                type,
                if (type == Type.EFFECT) normalizeEffectIds(values) else values,
                condition.get("negated")?.asBoolean ?: false,
            )
        }.orEmpty()
    }
}
