package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.api.config.v1.collect.impl.OneConfigCollector
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.internal.ui.api.Tooltip
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.dropdown.SimpleDropdownMenu
import org.polyfrost.oneconfig.internal.ui.components.effect.EffectIcon
import org.polyfrost.oneconfig.internal.ui.components.effect.EffectPicker
import org.polyfrost.oneconfig.internal.ui.components.effect.rememberEffectCatalog
import org.polyfrost.oneconfig.internal.ui.components.fadingEdges
import org.polyfrost.oneconfig.internal.ui.components.onClick
import org.polyfrost.oneconfig.internal.ui.components.picker.toggleEntry
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource
import org.polyfrost.oneconfig.internal.ui.components.settings.CheckboxIndicator
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Radii
import org.polyfrost.oneconfig.internal.ui.themes.concentric
import java.util.function.Predicate
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.internal.ui.components.settings.Option as OptionControl

const val OVERRIDE_GROUPS_MODEL = "overrideGroupsModel"
const val OVERRIDE_GROUPS_GLOBAL = "overrideGroupsGlobal"

@Option(display = OverrideGroupsVisualizer::class)
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class OverrideGroupsOption(
    val title: String = "",
    val description: String = "",
    val category: String = "General",
    val subcategory: String = "General",
)

private val ContainerPadding = 8.dp
private val CardSpacing = 8.dp
private val EntryHeight = 34.dp
private val EffectTileSize = 30.dp
private val PickerWidth = 380.dp
private val PickerShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(6.dp)
private val SummaryChipShape = RoundedCornerShape(4.dp)
private const val SUMMARY_ICONS = 4
private const val DELETE_CONFIRM_MS = 3000L

private val CATEGORY_CHOICES = buildList {
    add(CATEGORY_BENEFICIAL to "Beneficial")
    //? if > 1.8.9
    add(CATEGORY_NEUTRAL to "Neutral")
    add(CATEGORY_HARMFUL to "Harmful")
}

private val AMBIENT_CHOICES = listOf(AMBIENT to "Ambient", NON_AMBIENT to "Non-ambient")

private val DURATION_CHOICES = listOf(PERMANENT to "Permanent", FINITE to "Finite")

private fun choicesFor(type: OverrideCondition.Type): List<Pair<String, String>> = when (type) {
    OverrideCondition.Type.EFFECT -> emptyList()
    OverrideCondition.Type.CATEGORY -> CATEGORY_CHOICES
    OverrideCondition.Type.AMBIENT -> AMBIENT_CHOICES
    OverrideCondition.Type.DURATION -> DURATION_CHOICES
}

private val CONDITION_TYPES = OverrideCondition.Type.entries.map { it to it.title }

class OverrideGroupsVisualizer : Visualizer {
    @Composable
    override fun visualize(prop: Property<*>) {
        val model = prop.getMetadata<OverrideGroupsModel>(OVERRIDE_GROUPS_MODEL) ?: return
        val global = prop.getMetadata<EffectComponentSettings>(OVERRIDE_GROUPS_GLOBAL) ?: EffectComponentSettings.DEFAULTS
        (prop.get() as? String)?.let(model::sync)

        val theme = LocalTheme.current
        val containerShape = theme.modCardShape
        val groups = remember(model.revision) { model.groups.toList() }
        var expanded by remember(model) { mutableStateOf<Int?>(null) }

        Column(
            modifier = Modifier
                .width(LocalOptionWidth.current)
                .clip(containerShape)
                .background(theme.componentBackground, containerShape)
                .border(1.dp, theme.borderColor, containerShape)
                .padding(ContainerPadding),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            ReorderableGroups(
                groups = groups,
                onMove = { from, to ->
                    model.groups.add(to, model.groups.removeAt(from))
                    model.changed()
                },
            ) { group, handle ->
                GroupCard(
                    group = group,
                    model = model,
                    global = global,
                    expanded = expanded == group.uid,
                    onToggleExpanded = { expanded = if (expanded == group.uid) null else group.uid },
                    onDuplicate = {
                        val copy = group.copy(name = uniqueName(model.groups, "${group.name} Copy"))
                        model.groups.add(model.groups.indexOf(group) + 1, copy)
                        model.changed()
                        expanded = copy.uid
                    },
                    onDelete = {
                        model.groups.remove(group)
                        model.changed()
                    },
                    dragHandle = handle,
                )
            }

            ActionButton("plus", "New override group", containerShape.concentric(ContainerPadding)) {
                val group = OverrideGroup(name = uniqueName(model.groups, "Group ${model.groups.size + 1}"))
                model.groups.add(group)
                model.changed()
                expanded = group.uid
            }
        }
    }
}

private fun uniqueName(groups: List<OverrideGroup>, base: String): String {
    val taken = groups.mapTo(HashSet()) { it.name }
    if (base !in taken) return base
    var i = 2
    while ("$base $i" in taken) i++
    return "$base $i"
}

/**
 * A column of cards that can be dragged by their handle where cards may differ in height, unlike the
 * fixed-height lists OneConfig provides
 */
@Composable
private fun ReorderableGroups(
    groups: List<OverrideGroup>,
    onMove: (from: Int, to: Int) -> Unit,
    card: @Composable (group: OverrideGroup, handle: Modifier) -> Unit,
) {
    val spacingPx = with(LocalDensity.current) { CardSpacing.toPx() }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var draggingUid by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    val dragIndex = groups.indexOfFirst { it.uid == draggingUid }
    fun stride(index: Int): Float = (heights[groups[index].uid] ?: 0) + spacingPx

    val targetIndex = if (dragIndex < 0) -1 else {
        var target = dragIndex
        var passed = 0f
        if (dragOffset > 0f) {
            for (j in dragIndex + 1..groups.lastIndex) {
                if (dragOffset <= passed + stride(j) / 2f) break
                target = j
                passed += stride(j)
            }
        } else {
            for (j in dragIndex - 1 downTo 0) {
                if (-dragOffset <= passed + stride(j) / 2f) break
                target = j
                passed += stride(j)
            }
        }
        target
    }

    val draggableState: DraggableState = rememberDraggableState { delta ->
        if (dragIndex < 0) return@rememberDraggableState
        val min = -(0 until dragIndex).sumOf { stride(it).toDouble() }.toFloat()
        val max = (dragIndex + 1..groups.lastIndex).sumOf { stride(it).toDouble() }.toFloat()
        dragOffset = (dragOffset + delta).coerceIn(min, max)
    }

    Column(verticalArrangement = Arrangement.spacedBy(CardSpacing)) {
        groups.forEachIndexed { index, group ->
            key(group.uid) {
                val isDragging = index == dragIndex
                val shift = when {
                    dragIndex < 0 || isDragging -> 0f
                    index in (dragIndex + 1)..targetIndex -> -stride(dragIndex)
                    index in targetIndex until dragIndex -> stride(dragIndex)
                    else -> 0f
                }
                // snap rather than animate once a drop lands, as the column has already moved the card
                val animated = remember { Animatable(0f) }
                LaunchedEffect(shift, dragIndex >= 0) {
                    if (dragIndex >= 0) animated.animateTo(shift) else animated.snapTo(0f)
                }
                val offset = when {
                    isDragging -> dragOffset
                    dragIndex >= 0 -> animated.value
                    else -> 0f
                }

                val handle = Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state = draggableState,
                    onDragStarted = {
                        draggingUid = group.uid
                        dragOffset = 0f
                    },
                    onDragStopped = {
                        val from = groups.indexOfFirst { it.uid == draggingUid }
                        val to = targetIndex
                        draggingUid = null
                        dragOffset = 0f
                        if (from >= 0 && to >= 0 && from != to) onMove(from, to)
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[group.uid] = it.height }
                        .offset { IntOffset(0, offset.roundToInt()) }
                        .zIndex(if (isDragging) 1f else 0f),
                ) {
                    card(group, handle)
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: OverrideGroup,
    model: OverrideGroupsModel,
    global: EffectComponentSettings,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: Modifier,
) {
    val theme = LocalTheme.current
    val shape = theme.modCardShape.concentric(ContainerPadding)
    val headerInteraction = rememberInteractionSource()
    val hovered by headerInteraction.collectIsHoveredAsState()
    val contentColor by animateColorAsState(if (hovered || expanded) theme.textColor else theme.textColorSecondary)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 90f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.modCardBackground, shape)
            .border(1.dp, if (expanded) Accent.copy(alpha = 0.6f) else theme.borderColor, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(headerInteraction)
                .pointerHoverIcon(PointerIcon.Hand)
                .onClick(headerInteraction, onToggleExpanded)
                .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                "dots-grid",
                modifier = dragHandle.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                color = contentColor,
            )
            CheckboxIndicator(group.enabled, onToggle = {
                group.enabled = !group.enabled
                model.changed()
            })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    group.name.ifBlank { "Unnamed group" },
                    color = if (group.enabled) theme.textColor else theme.textColorSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GroupSummary(group)
            }
            Icon("up", modifier = Modifier.size(10.dp).rotate(chevronRotation), color = contentColor)
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            GroupBody(group, model, global, onDuplicate, onDelete)
        }
    }
}

@Composable
private fun GroupSummary(group: OverrideGroup) {
    val theme = LocalTheme.current
    val rules = group.rules.filter { it.isActive }
    if (rules.isEmpty()) {
        Text("No rules yet", color = theme.textColorSecondary, fontSize = 11.sp, maxLines = 1)
        return
    }

    @Composable
    fun Words(text: String) = Text(
        text,
        color = theme.textColorSecondary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    val chipped = rules.size > 1
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (group.match == Match.NONE) Words("None of:")
        rules.forEachIndexed { index, rule ->
            if (index > 0) Words("or")
            Row(
                modifier = if (!chipped) Modifier else Modifier
                    .clip(SummaryChipShape)
                    .background(theme.chipBackground, SummaryChipShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rule.conditions.filter { it.isActive }.forEachIndexed { i, condition ->
                    if (i > 0) Words("&")
                    if (condition.type == OverrideCondition.Type.EFFECT) {
                        // icons read faster than names, negated or not
                        if (condition.negated) Words("not")
                        for (id in condition.values.take(SUMMARY_ICONS)) EffectIcon(id, Modifier.size(14.dp))
                        if (condition.values.size > SUMMARY_ICONS) Words("+${condition.values.size - SUMMARY_ICONS}")
                    } else {
                        Words(describe(condition))
                    }
                }
            }
        }
    }
}

private fun describe(condition: OverrideCondition): String {
    val labels = when (condition.type) {
        OverrideCondition.Type.EFFECT -> condition.values.map { EffectCatalog.byId[it]?.title ?: it }
        else -> condition.values.map { value -> choicesFor(condition.type).firstOrNull { it.first == value }?.second ?: value }
    }
    val text = labels.joinToString("/")
    return if (condition.negated) "not $text" else text
}

@Composable
private fun GroupBody(
    group: OverrideGroup,
    model: OverrideGroupsModel,
    global: EffectComponentSettings,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalTheme.current
    val innerShape = RoundedCornerShape(Radii.SM)

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NameField(group.name, innerShape) {
            group.name = it
            model.changed()
        }

        SectionLabel("Applies when")
        Segmented(
            options = Match.entries.map { it to it.title },
            selected = group.match,
            shape = innerShape,
        ) {
            group.match = it
            model.changed()
        }
        Text(
            when (group.match) {
                Match.ANY -> "Applies when any rule below matches. A rule matches when all of its conditions do."
                Match.NONE -> "Applies when no rule below matches. A rule matches when all of its conditions do."
            },
            color = theme.textColorSecondary,
            fontSize = 11.sp,
        )

        group.rules.forEachIndexed { index, rule ->
            key(rule.uid) {
                if (index > 0) Joiner("or")
                RuleCard(
                    index = index,
                    rule = rule,
                    shape = innerShape,
                    onChange = model::changed,
                    onRemove = {
                        group.rules.remove(rule)
                        model.changed()
                    },
                )
            }
        }

        MenuButton(
            icon = "plus",
            label = "Add rule",
            shape = innerShape,
            sections = listOf(null to CONDITION_TYPES),
        ) { type ->
            group.rules += OverrideRule(listOf(OverrideCondition(type)))
            model.changed()
        }

        SectionLabel("Overridden options")
        OverriddenOptions(group, model, global, innerShape)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("copy", "Duplicate", innerShape, Modifier.weight(1f), onClick = onDuplicate)
            DeleteButton(innerShape, Modifier.weight(1f), onDelete)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalTheme.current.textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun NameField(value: String, shape: Shape, onChange: (String) -> Unit) {
    val theme = LocalTheme.current
    val interaction = rememberInteractionSource()
    val focused by interaction.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = theme.textColor, fontSize = 12.sp, fontFamily = theme.typography.family),
        cursorBrush = SolidColor(theme.textColor),
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().trackTextInputFocus(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(shape)
                    .background(theme.componentBackground, shape)
                    .border(1.dp, if (focused) Accent else theme.borderColor, shape)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Group name", color = theme.textColorSecondary, fontSize = 12.sp)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, shape: Shape, onSelect: (T) -> Unit) {
    val theme = LocalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(shape)
            .background(theme.componentBackground, shape)
            .border(1.dp, theme.borderColor, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, label) in options) {
            val active = value == selected
            val interaction = rememberInteractionSource()
            val hovered by interaction.collectIsHoveredAsState()
            val background by animateColorAsState(
                when {
                    active -> Accent
                    hovered -> theme.modCardBackground
                    else -> theme.modCardBackground.copy(alpha = 0f)
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(PillShape)
                    .background(background, PillShape)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .onClick(interaction) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) theme.accentTextColor else theme.textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RuleCard(index: Int, rule: OverrideRule, shape: Shape, onChange: () -> Unit, onRemove: () -> Unit) {
    val theme = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.componentBackground, shape)
            .border(1.dp, if (rule.isActive) theme.borderColor else theme.borderColor.copy(alpha = 0.4f), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Rule ${index + 1}",
                modifier = Modifier.weight(1f),
                color = theme.textColorSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            HoverIcon("trash", onClick = onRemove)
        }

        rule.conditions.forEachIndexed { i, condition ->
            key(System.identityHashCode(condition)) {
                if (i > 0) Joiner("and")
                ConditionSection(
                    condition = condition,
                    shape = shape,
                    negationLocked = condition.type == OverrideCondition.Type.EFFECT && rule.locksEffectNegation,
                    onChange = onChange,
                    onRemove = {
                        rule.conditions.remove(condition)
                        if (rule.conditions.isEmpty()) onRemove() else onChange()
                    },
                )
            }
        }

        val addable = CONDITION_TYPES.filter { rule.canAdd(it.first) }
        if (addable.isNotEmpty()) {
            MenuButton(icon = "plus", label = "Add condition", shape = shape, sections = listOf(null to addable)) { type ->
                rule.add(type)
                onChange()
            }
        }
    }
}

@Composable
private fun Joiner(word: String) {
    val theme = LocalTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(theme.borderColor))
        Text(word, color = theme.textColorSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.weight(1f).height(1.dp).background(theme.borderColor))
    }
}

@Composable
private fun ConditionSection(
    condition: OverrideCondition,
    shape: Shape,
    negationLocked: Boolean,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    val theme = LocalTheme.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(condition.type.title, color = theme.textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (condition.type.choices == null) {
                Pill(if (condition.negated) "is not" else "is", selected = condition.negated, enabled = !negationLocked) {
                    condition.negated = !condition.negated
                    onChange()
                }
            }
            Box(Modifier.weight(1f))
            HoverIcon("trash", onClick = onRemove)
        }

        when (condition.type) {
            OverrideCondition.Type.EFFECT -> EffectValues(condition, onChange)
            OverrideCondition.Type.CATEGORY -> ChoiceValues(condition, CATEGORY_CHOICES, onChange)
            OverrideCondition.Type.AMBIENT, OverrideCondition.Type.DURATION -> {
                val choices = choicesFor(condition.type)
                Segmented(choices, condition.values.firstOrNull() ?: choices.first().first, shape) {
                    condition.values = listOf(it)
                    onChange()
                }
            }
        }

        if (!condition.isActive) {
            Text("Pick at least one, or this condition is ignored.", color = theme.textColorSecondary, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceValues(condition: OverrideCondition, choices: List<Pair<String, String>>, onChange: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((value, label) in choices) {
            Pill(label, selected = value in condition.values) {
                condition.values = if (value in condition.values) condition.values - value
                else choices.map { it.first }.filter { it in condition.values || it == value }
                onChange()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectValues(condition: OverrideCondition, onChange: () -> Unit) {
    val theme = LocalTheme.current
    var picking by remember { mutableStateOf(false) }
    val catalog = rememberEffectCatalog()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (id in condition.values) {
            key(id) {
                EffectTile(id) {
                    condition.values = condition.values - id
                    onChange()
                }
            }
        }

        Box {
            val interaction = rememberInteractionSource()
            val hovered by interaction.collectIsHoveredAsState()
            val color by animateColorAsState(if (hovered || picking) theme.textColor else theme.textColorSecondary)
            Box(
                modifier = Modifier
                    .size(EffectTileSize)
                    .clip(PillShape)
                    .border(1.dp, theme.borderColor, PillShape)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .onClick(interaction) { picking = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon("plus", modifier = Modifier.size(14.dp), color = color)
            }

            SimpleDropdownMenu(
                expanded = picking,
                onDismissRequest = { picking = false },
                offset = DpOffset(0.dp, 8.dp),
                modifier = Modifier.width(PickerWidth),
            ) {
                EffectPicker(
                    effects = catalog,
                    selected = condition.values,
                    modifier = Modifier
                        .clip(PickerShape)
                        .background(theme.popupBackground, PickerShape)
                        .border(1.dp, theme.borderColor, PickerShape)
                        .padding(vertical = 12.dp),
                    onToggle = { id ->
                        condition.values = toggleEntry(condition.values, id, maxEntries = 0)
                        onChange()
                    },
                    onClose = { picking = false },
                    titleKey = "Effects",
                    searchPlaceholderKey = "Search effects",
                    emptyKey = "No effects found",
                )
            }
        }
    }
}

/** A picked effect where hovering shows it can be clicked away */
@Composable
private fun EffectTile(id: String, onRemove: () -> Unit) {
    val theme = LocalTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) Color(0xFFFF5555) else theme.borderColor)
    val title = EffectCatalog.byId[id]?.title ?: id

    Tooltip(
        text = { Text("$title (click to remove)", color = theme.textColor, fontSize = 11.sp) },
        modifier = Modifier,
        anchor = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .size(EffectTileSize)
                .clip(PillShape)
                .background(theme.modCardBackground, PillShape)
                .border(1.dp, border, PillShape)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .onClick(interaction, onRemove)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            EffectIcon(id, Modifier.fillMaxSize(), alpha = if (hovered) 0.5f else 1f)
            if (hovered) Icon("close", modifier = Modifier.size(12.dp), color = theme.textColor)
        }
    }
}

@Composable
private fun OverriddenOptions(group: OverrideGroup, model: OverrideGroupsModel, global: EffectComponentSettings, shape: Shape) {
    val theme = LocalTheme.current
    val props = remember(group.uid) { optionProperties(group.settings, model) }
    val settings = group.settings
    val chosen = remember(model.revision, props) { settings.overriddenOptions.filter { it in props } }

    if (chosen.isEmpty()) {
        Text("Nothing overridden yet, so matching effects use Global.", color = theme.textColorSecondary, fontSize = 11.sp)
    }

    for (field in chosen) {
        key(field) {
            OptionEntry(props.getValue(field), shape) {
                settings.copyField(field, EffectComponentSettings.DEFAULTS)
                settings.overriddenOptions = settings.overriddenOptions.filter { it != field }.toTypedArray()
                model.changed()
            }
        }
    }

    val remaining = props.keys.filter { it !in chosen }
    if (remaining.isNotEmpty()) {
        val sections = remaining
            .groupBy { props.getValue(it).getMetadata<String>("subcategory") }
            .map { (section, fields) -> section to fields.map { it to (props.getValue(it).title?.toString() ?: it) } }
        MenuButton(icon = "plus", label = "Add option", shape = shape, sections = sections) { field ->
            settings.copyField(field, global)
            settings.overriddenOptions = EffectComponentSettings.OPTION_FIELDS
                .filter { it in settings.overriddenOptions || it == field }
                .toTypedArray()
            model.changed()
        }
    }
}

private fun optionProperties(settings: EffectComponentSettings, model: OverrideGroupsModel): Map<String, Property<*>> {
    val tree = Tree.tree("overrideGroup")
    OneConfigCollector().handle(tree, settings, 0)
    return EffectComponentSettings.OPTION_FIELDS.mapNotNull { field ->
        val prop = tree.getProp(field) ?: return@mapNotNull null
        Property.recast<Any?>(prop).addCallback(Predicate { model.changed(); false })
        field to prop
    }.toMap()
}

@Composable
private fun OptionEntry(prop: Property<*>, shape: Shape, onRemove: () -> Unit) {
    val theme = LocalTheme.current
    val inline = prop.get() is Boolean

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.componentBackground, shape)
            .border(1.dp, theme.borderColor, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                prop.title ?: prop.id,
                modifier = Modifier.weight(1f),
                color = theme.textColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (inline) OptionControl(prop)
            HoverIcon("trash", onClick = onRemove)
        }
        if (!inline) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalOptionWidth provides maxWidth) {
                    OptionControl(prop)
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Accent
            hovered && enabled -> theme.chipBackground.copy(alpha = 1f).compositeOver(theme.componentBackground)
            else -> theme.chipBackground
        }
    )
    Box(
        modifier = Modifier
            .height(24.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(PillShape)
            .background(background, PillShape)
            .border(1.dp, theme.borderColor, PillShape)
            .hoverable(interaction)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand).onClick(interaction, onClick) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) theme.accentTextColor else theme.textColor, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun HoverIcon(icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(if (hovered) theme.textColor else theme.textColorSecondary)
    Icon(
        icon,
        modifier = modifier
            .size(14.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onClick(interaction, onClick),
        color = color,
    )
}

@Composable
private fun ActionButton(
    icon: String,
    label: String,
    shape: Shape,
    modifier: Modifier = Modifier.fillMaxWidth(),
    highlight: Color? = null,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(highlight ?: if (hovered) theme.textColor else theme.textColorSecondary)

    Row(
        modifier = modifier
            .height(EntryHeight)
            .clip(shape)
            .border(1.dp, highlight ?: theme.borderColor, shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onClick(interaction, onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, modifier = Modifier.size(14.dp), color = color)
        Text(label, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeleteButton(shape: Shape, modifier: Modifier, onDelete: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(DELETE_CONFIRM_MS)
            armed = false
        }
    }
    ActionButton(
        icon = "trash",
        label = if (armed) "Confirm" else "Delete",
        shape = shape,
        modifier = modifier,
        highlight = if (armed) Color(0xFFFF5555) else null,
    ) {
        if (armed) onDelete() else armed = true
    }
}

@Composable
private fun <T> MenuButton(
    icon: String,
    label: String,
    shape: Shape,
    sections: List<Pair<String?, List<Pair<T, String>>>>,
    onPick: (T) -> Unit,
) {
    val theme = LocalTheme.current
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
        ActionButton(icon, label, shape) { open = !open }

        SimpleDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 6.dp),
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
        ) {
            val menuShape = theme.sideBarNavigationEntryShape
            val itemShape = menuShape.concentric(4.dp)
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .clip(menuShape)
                    .background(theme.componentBackground, menuShape)
                    .border(1.dp, theme.borderColor, menuShape),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .fadingEdges(scrollState, theme.componentBackground)
                        .verticalScroll(scrollState)
                        .padding(4.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for ((section, entries) in sections) {
                        if (section != null && sections.size > 1) {
                            Text(
                                section,
                                color = theme.textColorSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
                            )
                        }
                        for ((value, entryLabel) in entries) {
                            val interaction = rememberInteractionSource()
                            val hovered by interaction.collectIsHoveredAsState()
                            val background by animateColorAsState(
                                if (hovered) theme.modCardBackground else theme.modCardBackground.copy(alpha = 0f)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(background, itemShape)
                                    .hoverable(interaction)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .onClick(interaction) {
                                        open = false
                                        onPick(value)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(entryLabel, color = theme.textColor, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
