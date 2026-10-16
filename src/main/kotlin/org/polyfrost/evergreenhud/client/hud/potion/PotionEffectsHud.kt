package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
//? if > 1.8.9 {
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
//?} else {
/*import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.living.effect.StatusEffect
import net.minecraft.entity.living.effect.StatusEffectInstance as MobEffectInstance
*///?}
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.composables.width
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.mc.McFontQueue.measureWidth
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.VanillaHudCompat
import org.polyfrost.oneconfig.api.config.v1.Node
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.collect.impl.OneConfigCollector
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.Section
import org.polyfrost.oneconfig.internal.ui.components.effect.EffectCatalog as OneConfigEffects
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.oneconfig.utils.v1.MHUtils.setAccessible
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.cos

private const val ICON = 18f
private const val ICON_GAP = 3f
private const val LINE_GAP = 1f
private const val FONT_SIZE = 8f

const val ROMAN = 0

private const val NAME = "Name"
private const val DURATION = "Duration"
private const val AMPLIFIER = "Amplifier"
private const val AMBIENT_EFFECTS = "Ambient Effects"
private const val BENEFICIAL_EFFECTS = "Beneficial Effects"
private const val NEUTRAL_EFFECTS = "Neutral Effects"
private const val HARMFUL_EFFECTS = "Harmful Effects"

private const val INFINITE = "**:**"

private const val BLINK_PERIOD = 1000L
private const val BLINK_MIN_FADE = 0.25f

private const val EDITOR_DIM_ALPHA = 0.5f

private const val MODE_AUTO = 0
private const val MODE_FULL = 1
private const val MODE_SINGLE_LINE = 2
private const val MODE_STACKED = 3

private const val DIRECTION_AUTO = 0
private const val DIRECTION_VERTICAL = 1
private const val DIRECTION_HORIZONTAL = 2

private const val SECTION_SWITCH_DISTANCE = 4f

private val SECTION_GRID = arrayOf(
    arrayOf(Section.TopLeft, Section.TopCenter, Section.TopRight),
    arrayOf(Section.CenterLeft, Section.Center, Section.CenterRight),
    arrayOf(Section.BottomLeft, Section.BottomCenter, Section.BottomRight),
)

private const val PER_OPTION_OVERRIDES = 1
private const val OVERRIDE_GROUPS = 2
private const val FILTER_GROUPS = 3

class PotionEffectsHud : Hud(
    id = "potion_effects.json",
    title = "Potion Effects",
    category = Category.PLAYER,
) {
    companion object {
        private val ROMAN_VALUES = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        private val ROMAN_NUMERALS =
            arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

        private fun charWidth(scale: Float): Float =
            measureWidth?.invoke(" ", scale) ?: (FONT_SIZE * scale * 0.5f)

        private val EXAMPLES = listOf(
            Example("speed", 1200, 1),
            Example("strength", 140, 0),
            Example("slowness", 840, 0),
            Example("glowing", 500, 0),
            Example("regeneration", 60, 1, ambient = true),
        )

        private val EXAMPLE_EFFECTS: List<MobEffectInstance> by lazy {
            EXAMPLES.mapNotNull { example ->
                //? if > 1.8.9 {
                val registry = BuiltInRegistries.MOB_EFFECT
                val effect = registry.firstOrNull { registry.getKey(it)?.path == example.id } ?: return@mapNotNull null
                MobEffectInstance(registry.wrapAsHolder(effect), example.ticks, example.amplifier, example.ambient, true)
                //?} else {
                /*val effect = StatusEffect.get(example.id) ?: return@mapNotNull null
                MobEffectInstance(effect.id, example.ticks, example.amplifier, example.ambient, true)
                *///?}
            }
        }

        private val EFFECT_STRIPPED_FIELDS = setOf("categoryFilter")

        private val HIDDEN = Supplier { Property.Display.HIDDEN }

        private val iconPaint = Paint()
        fun roman(value: Int): String {
            if (value < 1 || value > 3999) return value.toString()
            val builder = StringBuilder()
            var left = value
            for (i in ROMAN_VALUES.indices) {
                while (left >= ROMAN_VALUES[i]) {
                    builder.append(ROMAN_NUMERALS[i])
                    left -= ROMAN_VALUES[i]
                }
            }
            return builder.toString()
        }

        fun formatDuration(ticks: Int): String {
            val total = ticks / 20
            val seconds = total % 60
            val minutes = total / 60 % 60
            val hours = total / 3600
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%d:%02d".format(minutes, seconds)
        }
    }

    private class Example(val id: String, val ticks: Int, val amplifier: Int, val ambient: Boolean = false)

    private data class Row(
        val icon: Image?,
        val name: String?,
        val duration: String?,
        val blinking: Boolean,
        val iconBlink: Boolean,
        val nameBlink: Boolean,
        val nameColor: PolyColor,
        val durationBlink: Boolean,
        val durationColor: PolyColor,
        val dimmed: Boolean,
    )

    private class RowStyle(
        val iconAlpha: Float,
        val nameFade: Float,
        val durationFade: Float,
        val nameColor: PolyColor,
        val durationColor: PolyColor,
    )

    private var categoryScopes = PerCategoryEffectSettings()
    private var effectScopes = PerEffectSettings()

    @DraggableList(
        title = "Sorting",
        description = "Which sorting rules to apply, by priority.",
        subcategory = "Sorting",
        checkable = true,
    )
    var sorting = emptyArray<String>()

    @Include
    var overrides = emptyArray<String>()

    @OverrideGroupsOption(
        title = "Override Groups",
        description = "Change how matching effects look.",
        subcategory = "Effects",
    )
    var overrideGroupsData = ""

    private var overrideGroups = OverrideGroupsModel()

    @Switch(title = "Reversed", subcategory = "Sorting")
    var reversed = false

    @Slider(title = "Spacing", min = 0F, max = 10F, step = 1F, subcategory = "Dimensions")
    var spacing = 2f

    @RadioButton(title = "Text Alignment", options = ["Auto", "Left", "Center", "Right"], subcategory = "Dimensions")
    var textAlignment = 0

    @RadioButton(title = "Layout Mode", options = ["Auto", "Full", "Single Line", "Stacked"], subcategory = "Dimensions")
    var layoutMode = MODE_AUTO

    @RadioButton(title = "List Direction", options = ["Auto", "Vertical", "Horizontal"], subcategory = "Dimensions")
    var listDirection = DIRECTION_AUTO

    @Switch(
        title = "Hide Vanilla Status Effects",
        description = "Turns off VanillaHUD's own status effects element, so it does not draw on top of this one.",
    )
    var hideVanillaEffects = false

    @Include
    var overrideOptionsVersion = 0

    private var rows = mutableStateOf<List<Row>>(emptyList())

    private data class Layout(val spacing: Float, val horizontal: Boolean, val mode: Int, val align: PolyAlign)

    private var layoutState = mutableStateOf(Layout(spacing, false, MODE_FULL, PolyAlign.Left))

    private var layoutSection = mutableStateOf<Section?>(null)
    private val activeSection: Section get() = layoutSection.value ?: section

    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var settledX = 0f
    private var settledY = 0f
    private var holdingSection = false
    private var heldAtX = 0f
    private var heldAtY = 0f

    private var blinkFade = mutableFloatStateOf(1f)

    private var tickHandler: EventHandler<*>? = null

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        overrideGroups.sync(overrideGroupsData)
        overrideGroups.persist = { overrideGroupsData = it }
        if (isReal) {
            hideIf("hideVanillaEffects") { !VanillaHudCompat.isPresent }
            tickHandler = eventHandler { _: TickEvent.End ->
                if (VanillaHudCompat.isPresent) VanillaHudCompat.hideStatusEffects(hideVanillaEffects && !hidden)
                updateAndRecalculate()
            }

            if (overrideOptionsVersion < OVERRIDE_GROUPS) {
                val scopes = legacyScopes()
                migrateLegacyEffectKeys()
                if (overrideOptionsVersion < PER_OPTION_OVERRIDES) migrateLegacyOverrides(scopes)
                migrateToOverrideGroups(scopes)
                overrideOptionsVersion = OVERRIDE_GROUPS
            }
            if (overrideOptionsVersion < FILTER_GROUPS) {
                overrideGroups.replace(FilterMigration.run(overrideGroups.groups, categoryScopes.global, EffectCatalog.ENTRIES))
                overrideOptionsVersion = FILTER_GROUPS
            }
            stripLegacyScopes()

            val groupsProp = Property.recast<String>(getProperty("overrideGroupsData"))
            groupsProp.addMetadata(OVERRIDE_GROUPS_MODEL, overrideGroups)
            groupsProp.addMetadata(OVERRIDE_GROUPS_GLOBAL, categoryScopes.global)
            overrideGroups.persist = { groupsProp.set(it) }
        }
    }

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    override fun remove() {
        tickHandler?.unregister()
        tickHandler = null
        if (VanillaHudCompat.isPresent) VanillaHudCompat.hideStatusEffects(false)
    }

    /** The fixed override slots used before override groups, kept only to migrate them */
    private class OverrideScope(
        val key: String,
        val title: String,
        val stripped: Set<String>,
        val category: String?,
        val isEffect: Boolean,
        val effectId: String? = null,
        val settings: () -> EffectComponentSettings,
    ) {
        fun overlaps(other: OverrideScope): Boolean = when {
            category == null || other.category == null -> true
            isEffect && other.isEffect -> false
            else -> category == other.category
        }

        fun condition(): OverrideCondition = when {
            isEffect -> OverrideCondition(OverrideCondition.Type.EFFECT, listOf(effectId ?: key))
            key == "ambient" -> OverrideCondition(OverrideCondition.Type.AMBIENT, listOf(AMBIENT))
            else -> OverrideCondition(OverrideCondition.Type.CATEGORY, listOf(key))
        }
    }

    private fun legacyScopes(): List<OverrideScope> = buildList {
        for (scope in categoryScopes.scopeDefs) {
            if (!scope.isOverride) continue
            val key = scope.key
            val category = key.takeIf { scope.title != AMBIENT_EFFECTS }
            add(OverrideScope(key, scope.title, scope.strippedFields, category, isEffect = false) { categoryScopes[key] })
        }
        for (entry in EffectCatalog.ENTRIES) {
            val path = entry.path
            add(OverrideScope(path, entry.title, EFFECT_STRIPPED_FIELDS, entry.category, isEffect = true, effectId = entry.id) { effectScopes.byPath.getValue(path) })
        }
    }

    /**
     * Turns every enabled legacy override into a group in the same order, then keeps any disabled one that
     * still holds changes as a disabled group so nothing is lost
     */
    private fun migrateToOverrideGroups(scopes: List<OverrideScope>) {
        val byTitle = scopes.associateBy { it.title }
        val enabled = overrides.mapNotNull { byTitle[it] }.distinct()
        val disabled = scopes.filter { it !in enabled && it.settings().overriddenOptions.isNotEmpty() }

        fun group(scope: OverrideScope, on: Boolean) = OverrideGroup(
            name = scope.title,
            enabled = on,
            rules = listOf(OverrideRule(listOf(scope.condition()))),
            settings = EffectComponentSettings().also { it.copyFrom(scope.settings()) },
        )

        val migrated = enabled.map { group(it, on = true) } + disabled.map { group(it, on = false) }
        if (migrated.isNotEmpty()) overrideGroups.replace(overrideGroups.groups + migrated)
        overrides = emptyArray()
    }

    /** The legacy slots only exist in the tree so old configs bind to them, and must not be saved or shown */
    private fun stripLegacyScopes() {
        val tree = tree ?: return
        for (scope in categoryScopes.scopeDefs) if (scope.isOverride) stripProperty(tree, scope.key)
        for (entry in EffectCatalog.ENTRIES) stripProperty(tree, entry.path)
        for (scope in categoryScopes.scopeDefs) if (scope.isOverride) categoryScopes[scope.key].copyFrom(EffectComponentSettings.DEFAULTS)
        for (settings in effectScopes.byPath.values) settings.copyFrom(EffectComponentSettings.DEFAULTS)
    }

    private fun migrateLegacyEffectKeys() {
        val tree = tree ?: return
        for ((legacy, path) in EffectCatalog.LEGACY_IDS) {
            val old = tree.get(legacy) as? Tree ?: continue
            val target = tree.get(path) as? Tree
            val settings = effectScopes.byPath[path]
            if (target != null && settings != null && settings.isDefault()) target.overwrite(old, false, false, null)
            stripProperty(tree, legacy)
        }
    }

    private fun migrateLegacyOverrides(scopes: List<OverrideScope>) {
        val global = categoryScopes.global
        val byTitle = scopes.associateBy { it.title }
        val enabled = overrides.mapNotNull { byTitle[it] }
        for (scope in scopes) {
            val settings = scope.settings()
            if (settings.overriddenOptions.isNotEmpty()) continue
            val priority = enabled.indexOf(scope)
            val fallbacks = if (priority < 0) emptyList() else {
                listOf(global) + enabled.drop(priority + 1).filter(scope::overlaps).map { it.settings() }
            }
            settings.overriddenOptions = EffectComponentSettings.LEAF_FIELDS
                .filter { field ->
                    field !in scope.stripped &&
                        (!settings.matches(field, EffectComponentSettings.DEFAULTS) || fallbacks.any { !settings.matches(field, it) })
                }
                .toTypedArray()
        }
    }

    override fun canMergeBackground(): Boolean = true

    override fun update(): Boolean {
        layoutState.value = Layout(spacing, direction(), layoutMode(), autoTextAlign())
        val next = buildRows()
        val rowsChanged = next != rows.value
        if (rowsChanged) rows.value = next
        val sectionChanged = settleLayoutSection()
        return rowsChanged || sectionChanged
    }

    override fun onEditorDragStart() {
        super.onEditorDragStart()
        dragging = true
        dragX = x
        dragY = y
    }

    override fun onEditorDragEnd() {
        super.onEditorDragEnd()
        dragging = false
    }

    override fun updateRelativeX(absX: Float) {
        super.updateRelativeX(absX)
        dragX = absX
    }

    override fun updateRelativeY(absY: Float) {
        super.updateRelativeY(absY)
        dragY = absY
    }

    private fun settleLayoutSection(): Boolean {
        val target = section
        val held = layoutSection.value

        if (held == target || held == null || !HudManager.isEditing || selfAnchorPoint != HudAnchor.Auto) {
            if (!dragging) {
                dragX = x
                dragY = y
            }
            if (holdingSection && dragX == heldAtX && dragY == heldAtY && held == target) return false
            holdingSection = false
            settledX = dragX
            settledY = dragY
            if (held == target) return false
            layoutSection.value = target
            return true
        }

        val row = towards(rowOf(held), rowOf(target), dragY - settledY)
        val column = towards(columnOf(held), columnOf(target), dragX - settledX)
        val next = SECTION_GRID[row][column]

        if (next != target) {
            section = next
            x = dragX
            y = dragY
            holdingSection = true
            heldAtX = dragX
            heldAtY = dragY
        }

        if (next == held) return false
        if (next == target) holdingSection = false
        layoutSection.value = next
        settledX = dragX
        settledY = dragY
        return true
    }

    private fun towards(from: Int, to: Int, moved: Float): Int = when {
        to > from && moved >= SECTION_SWITCH_DISTANCE -> to
        to < from && moved <= -SECTION_SWITCH_DISTANCE -> to
        else -> from
    }

    private fun rowOf(sec: Section): Int = SECTION_GRID.indexOfFirst { sec in it }

    private fun columnOf(sec: Section): Int = SECTION_GRID[rowOf(sec)].indexOf(sec)

    private fun buildRows(): List<Row> {
        val editing = HudManager.isEditing || !isReal

        //? if > 1.8.9 {
        val active = if (!isReal) emptyList() else mc.player?.activeEffects?.filter { it.showIcon() }.orEmpty()
        //?} else
        //val active = if (!isReal) emptyList() else mc.player?.activeEffects?.filter { StatusEffect.BY_ID.getOrNull(it.id)?.hasIcon() == true }.orEmpty()
        val effects = active.ifEmpty { if (editing) EXAMPLE_EFFECTS else return emptyList() }

        val visible = if (editing) effects else effects.filter(::shouldDisplayEffect)
        if (visible.isEmpty()) return emptyList()

        val ordered = sortEffects(visible, sorting, reversed)
        return ordered.map { effect -> row(effect, dimmed = editing && !shouldDisplayEffect(effect)) }
    }

    private fun sortEffects(effects: List<MobEffectInstance>, sortingStack: Array<String>, reversed: Boolean): List<MobEffectInstance> {
        var sorted = effects

        for (rule in sortingStack.reversed()) {
            sorted = sortByRule(sorted, rule)
        }

        return if (reversed) sorted.asReversed() else sorted
    }

    private fun sortByRule(effects: List<MobEffectInstance>, rule: String): List<MobEffectInstance> {
        return when (rule) {
            //? if > 1.8.9 {
            NAME -> effects.sortedBy { it.effect.value().displayName.string }
            //?} else
            //NAME -> effects.sortedBy { I18n.translate(it.name) }
            DURATION -> effects.sortedBy { if (it.isInfiniteDuration) Int.MAX_VALUE else it.duration }
            AMPLIFIER -> effects.sortedByDescending { it.amplifier }
            AMBIENT_EFFECTS -> effects.sortedBy { it.isAmbient }
            //? if > 1.8.9 {
            BENEFICIAL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.BENEFICIAL }
            NEUTRAL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.NEUTRAL }
            HARMFUL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.HARMFUL }
            //?} else {
            //BENEFICIAL_EFFECTS -> effects.sortedByDescending { !StatusEffect.BY_ID[it.id].isHarmful }
            //NEUTRAL_EFFECTS -> effects
            //HARMFUL_EFFECTS -> effects.sortedByDescending { StatusEffect.BY_ID[it.id].isHarmful }
            //?}
            else -> {
                val path = EffectCatalog.titleToPath[rule] ?: return effects
                //? if > 1.8.9 {
                effects.sortedByDescending { BuiltInRegistries.MOB_EFFECT.getKey(it.effect.value())?.path == path }
                //?} else
                //effects.sortedByDescending { EffectCatalog.pathOf(it.id) == path }
            }
        }
    }

    private fun shouldDisplayEffect(effect: MobEffectInstance): Boolean {
        val values = valuesFor(effect)

        if (!values.showEffects) return false

        val min = values.durationRange[0]
        val max = values.durationRange[1]
        if (min != 0f || max != 0f) {
            if (!effect.isInfiniteDuration && effect.duration / 20f !in min..max) return false
        }

        return true
    }

    private fun row(effect: MobEffectInstance, dimmed: Boolean = false): Row {
        //? if > 1.8.9 {
        val mobEffect = effect.effect.value()
        return row(
            BuiltInRegistries.MOB_EFFECT.getKey(mobEffect)?.toString(),
            mobEffect.displayName.string,
            effect.duration,
            effect.amplifier,
            effect.isInfiniteDuration,
            valuesFor(effect),
            dimmed,
        )
        //?} else {
        /*val statusEffect = StatusEffect.BY_ID[effect.id]
        return row(
            EffectCatalog.idOf(effect.id),
            I18n.translate(statusEffect.translationKey),
            effect.duration,
            effect.amplifier,
            effect.isInfiniteDuration,
            valuesFor(effect),
            dimmed,
        )
        *///?}
    }

    private fun row(id: String?, name: String, ticks: Int, amplifier: Int, infinite: Boolean, values: EffectComponentValues, dimmed: Boolean = false): Row {
        val level = amplifier + 1
        val amplifierText = if (values.showAmplifier && level > 1) {
            if (values.amplifierStyle == ROMAN) roman(level) else level.toString()
        } else null

        val title = when {
            values.nameEnabled && amplifierText != null -> "$name $amplifierText"
            values.nameEnabled -> name
            amplifierText != null -> amplifierText
            else -> null
        }

        val time = when {
            !values.durationEnabled -> null
            infinite -> INFINITE
            else -> formatDuration(ticks)
        }
        return Row(
            icon = if (values.iconEnabled && id != null) OneConfigEffects.icon(id) else null,
            name = title,
            duration = time,
            blinking = isBlinking(ticks, infinite, values.blinkThreshold),
            iconBlink = values.iconBlink,
            nameBlink = values.nameBlink,
            nameColor = values.nameColor,
            durationBlink = values.durationBlink,
            durationColor = values.durationColor,
            dimmed = dimmed,
        )
    }

    private fun isBlinking(ticks: Int, infinite: Boolean, blinkThreshold: Float): Boolean =
        !infinite && blinkThreshold > 0f && ticks <= blinkThreshold * 20f

    private fun currentBlinkFade(): Float {
        val phase = (System.currentTimeMillis() % BLINK_PERIOD).toFloat() / BLINK_PERIOD
        return BLINK_MIN_FADE + (1f - BLINK_MIN_FADE) * ((cos(phase * 2f * PI.toFloat()) + 1f) / 2f)
    }

    private fun textAlign(mode: Int): PolyAlign = when (mode) {
        2 -> PolyAlign.Center
        3 -> PolyAlign.Right
        else -> PolyAlign.Left
    }

    private fun autoTextAlign(): PolyAlign {
        if (textAlignment != 0) return textAlign(textAlignment)

        if (selfAnchorPoint != HudAnchor.Auto) {
            return horizontalFrom(selfAnchorPoint)
        }

        return when (activeSection) {
            Section.TopLeft, Section.CenterLeft, Section.BottomLeft -> PolyAlign.Left
            Section.TopCenter, Section.Center, Section.BottomCenter -> PolyAlign.Center
            Section.TopRight, Section.CenterRight, Section.BottomRight -> PolyAlign.Right
        }
    }

    private fun horizontalFrom(anchor: HudAnchor): PolyAlign = when (anchor) {
        HudAnchor.TopLeft, HudAnchor.Left, HudAnchor.BottomLeft -> PolyAlign.Left
        HudAnchor.Top, HudAnchor.Center, HudAnchor.Bottom -> PolyAlign.Center
        HudAnchor.TopRight, HudAnchor.Right, HudAnchor.BottomRight -> PolyAlign.Right
        HudAnchor.Auto -> PolyAlign.Left
    }

    private fun direction(): Boolean {
        if (listDirection != DIRECTION_AUTO) return listDirection == DIRECTION_HORIZONTAL

        if (selfAnchorPoint != HudAnchor.Auto) {
            return when (selfAnchorPoint) {
                HudAnchor.Top, HudAnchor.Center, HudAnchor.Bottom -> true
                else -> false
            }
        }

        return when (activeSection) {
            Section.TopCenter, Section.Center, Section.BottomCenter -> true
            else -> false
        }
    }

    private fun layoutMode(): Int {
        if (layoutMode != MODE_AUTO) return layoutMode

        if (selfAnchorPoint != HudAnchor.Auto) {
            return when (selfAnchorPoint) {
                HudAnchor.Top, HudAnchor.Center, HudAnchor.Bottom -> MODE_STACKED
                HudAnchor.Left, HudAnchor.Right -> MODE_FULL
                HudAnchor.TopLeft, HudAnchor.TopRight, HudAnchor.BottomLeft, HudAnchor.BottomRight -> MODE_FULL
                HudAnchor.Auto -> MODE_FULL
            }
        }

        return when (activeSection) {
            Section.TopCenter, Section.Center, Section.BottomCenter -> MODE_STACKED
            Section.CenterLeft, Section.CenterRight -> MODE_FULL
            Section.TopLeft, Section.TopRight, Section.BottomLeft, Section.BottomRight -> MODE_FULL
        }
    }

    private fun isBottomAligned(): Boolean {
        if (selfAnchorPoint != HudAnchor.Auto) {
            return when (selfAnchorPoint) {
                HudAnchor.BottomLeft, HudAnchor.Bottom, HudAnchor.BottomRight -> true
                else -> false
            }
        }
        return when (activeSection) {
            Section.BottomLeft, Section.BottomCenter, Section.BottomRight -> true
            else -> false
        }
    }

    private fun styleFor(row: Row): RowStyle {
        val dimAlpha = if (row.dimmed) EDITOR_DIM_ALPHA else 1f
        val fade = if (row.blinking) blinkFade.floatValue else 1f
        val iconAlpha = (if (row.iconBlink) fade else 1f) * dimAlpha
        val nameFade = (if (row.nameBlink) fade else 1f) * dimAlpha
        val durationFade = (if (row.durationBlink) fade else 1f) * dimAlpha
        return RowStyle(
            iconAlpha = iconAlpha,
            nameFade = nameFade,
            durationFade = durationFade,
            nameColor = PolyColor(faded(row.nameColor.argb, nameFade), row.nameColor.chroma, row.nameColor.chromaSpeed),
            durationColor = PolyColor(faded(row.durationColor.argb, durationFade), row.durationColor.chroma, row.durationColor.chromaSpeed),
        )
    }

    @Composable
    override fun Content() {
        val layout = layoutState.value
        val list = rows.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)

        if (list.any { it.blinking }) {
            LaunchedEffect(Unit) {
                while (true) withFrameMillis { blinkFade.floatValue = currentBlinkFade() }
            }
        }

        val maxRowWidth = remember(list, scale, font, layout.mode) {
            val mode = layout.mode
            val hasAnyIcon = list.any { it.icon != null }
            val iconWidth = ICON * scale
            val iconGap = ICON_GAP * scale

            list.maxOf { row ->
                val nameWidth = row.name?.let { measureWidth?.invoke(it, scale) ?: 0f } ?: 0f
                val durationWidth = row.duration?.let { measureWidth?.invoke(it, scale) ?: 0f } ?: 0f
                when (mode) {
                    MODE_STACKED -> maxOf(if (row.icon != null) iconWidth else 0f, nameWidth, durationWidth)
                    MODE_SINGLE_LINE -> {
                        val gapWidth = if (row.name != null && row.duration != null) charWidth(scale) else 0f
                        val textWidth = nameWidth + gapWidth + durationWidth
                        (if (hasAnyIcon) iconWidth + iconGap else 0f) + textWidth
                    }
                    else -> (if (hasAnyIcon) iconWidth + iconGap else 0f) + maxOf(nameWidth, durationWidth)
                }
            }
        }

        val horizontal = layout.horizontal
        val modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)

        PolyBox(modifier = modifier) {
            if (horizontal) {
                PolyRow(gap = layout.spacing * scale) {
                    for (row in list) Effect(row, scale, maxRowWidth, layout)
                }
            } else {
                PolyColumn(gap = layout.spacing * scale) {
                    for (row in list) Effect(row, scale, maxRowWidth, layout)
                }
            }
        }
    }

    @Composable
    private fun Effect(row: Row, scale: Float, maxRowWidth: Float, layout: Layout) {
        val mode = layout.mode
        if (mode == MODE_STACKED) {
            StackedEffect(row, scale, maxRowWidth)
            return
        }

        val style = styleFor(row)
        val align = layout.align

        val iconBlock: @Composable () -> Unit = {
            if (row.icon != null) Icon(row.icon, ICON * scale, style.iconAlpha)
        }

        PolyBox(modifier = PolyModifier.width(maxRowWidth)) {
            PolyRow(gap = ICON_GAP * scale, modifier = PolyModifier.align(align)) {
                if (align != PolyAlign.Right) iconBlock()

                if (row.name != null || row.duration != null) {
                    if (mode == MODE_SINGLE_LINE) CompactText(row, scale, style, align)
                    else FullText(row, scale, style, align)
                }

                if (align == PolyAlign.Right) iconBlock()
            }
        }
    }

    @Composable
    private fun CompactText(row: Row, scale: Float, style: RowStyle, align: PolyAlign) {
        PolyRow(gap = charWidth(scale), modifier = PolyModifier.align(PolyAlign.Center)) {
            if (align == PolyAlign.Right) {
                if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor)
                if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor)
            } else {
                if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor)
                if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor)
            }
        }
    }

    @Composable
    private fun FullText(row: Row, scale: Float, style: RowStyle, align: PolyAlign) {
        PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
            if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, modifier = PolyModifier.align(align))
            if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, modifier = PolyModifier.align(align))
        }
    }

    @Composable
    private fun StackedEffect(row: Row, scale: Float, maxWidth: Float) {
        val style = styleFor(row)
        val reversed = isBottomAligned()

        val iconBlock: @Composable () -> Unit = {
            if (row.icon != null) Icon(row.icon, ICON * scale, style.iconAlpha, modifier = PolyModifier.align(PolyAlign.Center))
        }
        val nameBlock: @Composable () -> Unit = {
            if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, modifier = PolyModifier.align(PolyAlign.Center))
        }
        val durationBlock: @Composable () -> Unit = {
            if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, modifier = PolyModifier.align(PolyAlign.Center))
        }

        PolyBox(modifier = PolyModifier.width(maxWidth)) {
            PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
                if (reversed) {
                    durationBlock(); nameBlock(); iconBlock()
                } else {
                    iconBlock(); nameBlock(); durationBlock()
                }
            }
        }
    }

    @Composable
    private fun Icon(icon: Image, size: Float, alpha: Float, modifier: PolyModifier = PolyModifier) {
        val src = remember(icon) { Rect.makeWH(icon.width.toFloat(), icon.height.toFloat()) }
        PolyCanvas(modifier.size(size, size)) { x, y, w, h ->
            iconPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            val downscaled = w * canvas.localToDeviceAsMatrix33.mat[0] < src.right
            val sampling = if (downscaled) SamplingMode.LINEAR else SamplingMode.DEFAULT
            canvas.drawImageRect(icon, src, Rect.makeXYWH(x, y, w, h), sampling, iconPaint, true)
        }
    }

    @Composable
    private fun Line(text: String, scale: Float, fade: Float, color: PolyColor, modifier: PolyModifier = PolyModifier) {
        if (font == Font.Minecraft) {
            PolyMcText(text, color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            PolyText(
                text,
                color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(faded(shadowColor, fade), shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
                modifier = modifier,
            )
        }
    }

    private fun faded(color: Int, fade: Float): Int {
        if (fade >= 1f) return color
        val alpha = ((color ushr 24 and 0xFF) * fade).toInt().coerceIn(0, 255)
        return (color and 0xFFFFFF) or (alpha shl 24)
    }

    override fun clone(): Hud = (super.clone() as PotionEffectsHud).also {
        it.sorting = sorting.copyOf()
        it.overrides = overrides.copyOf()
        it.overrideGroups = overrideGroups.deepCopy()
        it.layoutState = mutableStateOf(layoutState.value)
        it.blinkFade = mutableFloatStateOf(1f)
        it.tickHandler = null

        it.categoryScopes = categoryScopes.deepCopy()
        it.effectScopes = effectScopes.deepCopy()

        it.rows = mutableStateOf(emptyList())
        it.layoutSection = mutableStateOf(null)
    }

    private class PerCategoryEffectSettings {
        val global = EffectComponentSettings()
        val beneficial = EffectComponentSettings()
        val neutral = EffectComponentSettings()
        val harmful = EffectComponentSettings()
        val ambient = EffectComponentSettings()

        val scopeDefs: List<ScopeDef> get() = listOf(
            ScopeDef("global", "Global", global, strippedFields = setOf(OVERRIDDEN_OPTIONS), isOverride = false),
            ScopeDef("beneficial", BENEFICIAL_EFFECTS, beneficial, strippedFields = setOf("categoryFilter")),
            ScopeDef("neutral", NEUTRAL_EFFECTS, neutral, strippedFields = setOf("categoryFilter")),
            ScopeDef("harmful", HARMFUL_EFFECTS, harmful, strippedFields = setOf("categoryFilter")),
            ScopeDef("ambient", AMBIENT_EFFECTS, ambient, strippedFields = setOf("ambientFilter", "categoryFilter", "permanentEffects", "emittingParticleEffects")),
        )

        operator fun get(key: String): EffectComponentSettings = when (key) {
            "beneficial" -> beneficial
            "neutral" -> neutral
            "harmful" -> harmful
            "ambient" -> ambient
            else -> global
        }

        fun copyFrom(other: PerCategoryEffectSettings) {
            global.copyFrom(other.global)
            beneficial.copyFrom(other.beneficial)
            neutral.copyFrom(other.neutral)
            harmful.copyFrom(other.harmful)
            ambient.copyFrom(other.ambient)
        }

        fun deepCopy(): PerCategoryEffectSettings = PerCategoryEffectSettings().also { it.copyFrom(this) }
    }

    override fun addToSerialized(tree: Tree) {
        super.addToSerialized(tree)
        val collector = OneConfigCollector()

        val liveSortOptions = buildList {
            addAll(listOf(NAME, DURATION, AMPLIFIER, AMBIENT_EFFECTS, BENEFICIAL_EFFECTS, NEUTRAL_EFFECTS, HARMFUL_EFFECTS))
            addAll(EffectCatalog.ENTRIES.map { it.title })
        }.toTypedArray()

        val sortingProp = tree.getProp("sorting") ?: throw IllegalStateException("sorting property not found on tree")
        sortingProp.addMetadata("options", liveSortOptions)

        for (scope in categoryScopes.scopeDefs) {
            val t = Tree.tree(scope.key)
            t.addMetadata(mapOf(
                "title" to scope.title, "description" to scope.description,
                "category" to "General", "subcategory" to "Effects",
                "collapsed" to (scope.key != "global")
            ))
            collector.handle(t, scope.settings, 0)
            for (field in scope.strippedFields) stripProperty(t, field)
            for (field in EffectComponentSettings.RETIRED_FIELDS) t.getProp(field)?.addDisplayCondition(HIDDEN)
            tree.put(t)
        }

        for (entryDef in EffectCatalog.ENTRIES) {
            val settings = effectScopes.byPath.getValue(entryDef.path)
            val t = Tree.tree(entryDef.path)
            t.addMetadata(mapOf(
                "title" to entryDef.title,
                "description" to "Overrides for the ${entryDef.title} effect.",
                "category" to "General", "subcategory" to "Effects",
                "collapsed" to true
            ))
            collector.handle(t, settings, 0)
            for (field in EFFECT_STRIPPED_FIELDS) stripProperty(t, field)
            tree.put(t)
        }
    }

    private fun valuesFor(effect: MobEffectInstance): EffectComponentValues {
        //? if > 1.8.9 {
        val mobEffect = effect.effect.value()
        val id = BuiltInRegistries.MOB_EFFECT.getKey(mobEffect)?.toString()
        val category = when (mobEffect.category) {
            MobEffectCategory.BENEFICIAL -> CATEGORY_BENEFICIAL
            MobEffectCategory.NEUTRAL -> CATEGORY_NEUTRAL
            MobEffectCategory.HARMFUL -> CATEGORY_HARMFUL
        }
        //?} else {
        /*val id = EffectCatalog.idOf(effect.id)
        val category = if (StatusEffect.BY_ID[effect.id].isHarmful) CATEGORY_HARMFUL else CATEGORY_BENEFICIAL
        *///?}

        overrideGroups.sync(overrideGroupsData)
        val facts = EffectFacts(id, category, effect.isAmbient, effect.isInfiniteDuration)
        val layers = overrideGroups.groups.mapNotNull { group -> group.settings.takeIf { group.matches(facts) } }
        return if (layers.isEmpty()) categoryScopes.global else LayeredEffectValues(layers, categoryScopes.global)
    }

    private class PerEffectSettings {
        val byPath: Map<String, EffectComponentSettings> =
            EffectCatalog.ENTRIES.associate { it.path to EffectComponentSettings() }

        fun deepCopy(): PerEffectSettings = PerEffectSettings().also { copy ->
            for ((path, settings) in byPath) {
                copy.byPath[path]?.copyFrom(settings)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun stripProperty(t: Tree, key: String) {
        val theMapField = Tree::class.java.getDeclaredField("theMap")
        theMapField.setAccessible()
        val map = theMapField.get(t) as LinkedHashMap<String, Node>
        map.remove(key)
    }
}
