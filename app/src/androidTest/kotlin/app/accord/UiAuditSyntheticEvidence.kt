package app.accord

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.theme.ThemePreference
import java.io.File

/** Opt-in capture of existing scenarios without changing their normal test mode. */
internal object UiAuditSyntheticEvidence {
    private val args get() = InstrumentationRegistry.getArguments()
    val enabled get() = args.containsKey("auditRound")
    val theme: ThemePreference?
        get() = when (args.getString("auditTheme")) {
            "light" -> ThemePreference.Light
            "dark" -> ThemePreference.Dark
            else -> null
        }

    fun capture(compose: ComposeTestRule, name: String, theme: ThemePreference, frameKind: String = "synthetic-window") {
        val epsilon = 0.0001f
        val round = requireNotNull(args.getString("auditRound"))
        require(round.matches(Regex("[a-z0-9_-]+")))
        val resolvedTheme = theme.name.lowercase()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screens/ui-audit/$round/$resolvedTheme")
        check(dir.exists() || dir.mkdirs())
        fun frame(suffix: String) {
            compose.waitForIdle()
            Thread.sleep(600)
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val file = "synthetic_${name}$suffix.png"
            File(dir, file).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val manifest = File(dir, "manifest.tsv")
            if (!manifest.exists()) manifest.writeText("file\tphase\ttheme\tfontScale\tdevice\tframe\n")
            manifest.appendText("$file\t$round\t$resolvedTheme\t${context.resources.configuration.fontScale}\t${android.os.Build.MODEL}\t$frameKind\n")
        }

        data class Target(val id: Int, val order: Int, val vertical: Boolean, val initial: Float, val area: Float)
        fun node(target: Target) = compose.onNode(
            SemanticsMatcher("scroll node ${target.id}") { it.id == target.id }, useUnmergedTree = true,
        )
        fun axis(target: Target) = node(target).fetchSemanticsNode().config.getOrNull(
            if (target.vertical) SemanticsProperties.VerticalScrollAxisRange else SemanticsProperties.HorizontalScrollAxisRange,
        ) ?: error("Scroll axis disappeared: $name / ${target.order} / ${target.vertical}")
        // Dialogs have a separate root; never sweep the screen behind the active window.
        fun discover(): List<Target> {
            val root = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().lastOrNull()
                ?: error("No semantics root for $name")
            val activeIds = mutableSetOf<Int>()
            fun collect(current: SemanticsNode) {
                activeIds += current.id
                current.children.forEach(::collect)
            }
            collect(root)
            return compose.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.id in activeIds }.flatMapIndexed { index, scroll ->
                    val area = scroll.boundsInRoot.width * scroll.boundsInRoot.height
                    listOfNotNull(
                        scroll.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                            ?.let { Target(scroll.id, index, true, it.value(), area) },
                        scroll.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                            ?.let { Target(scroll.id, index, false, it.value(), area) },
                    )
                }
        }
        fun reset(target: Target) {
            val current = axis(target)
            if (current.value() <= epsilon) return
            node(target).performSemanticsAction(SemanticsActions.ScrollBy) {
                if (target.vertical) it(0f, -1_000_000f) else it(-1_000_000f, 0f)
            }
            compose.waitForIdle()
            check(axis(target).value() <= epsilon) {
                "Could not return to start: $name / ${target.order} / ${target.vertical}"
            }
        }
        // Some historical fixtures enter in the middle or at the end of a conversation.
        val initialTargets = discover()
        if (initialTargets.any { it.initial > epsilon }) frame("_entrada")
        initialTargets.filter { it.vertical }.sortedBy { it.area }.forEach(::reset)
        frame("")
        // A LazyColumn can dispose and recreate a horizontal child while it scrolls. Discover
        // visible horizontal rows at each viewport instead of retaining their semantics IDs.
        val seenHorizontal = mutableSetOf<String>()
        var horizontalNumber = 0
        fun sweepVisibleHorizontal() {
            val rootBounds = compose.onAllNodes(isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes().last().boundsInRoot
            for (target in discover().filter { !it.vertical }) {
                val scroll = node(target).fetchSemanticsNode()
                val outerBounds = discover().filter { it.vertical }.maxByOrNull { it.area }
                    ?.let { node(it).fetchSemanticsNode().boundsInRoot } ?: rootBounds
                val visible = scroll.boundsInRoot.bottom > maxOf(rootBounds.top, outerBounds.top) + 1f &&
                    scroll.boundsInRoot.top < minOf(rootBounds.bottom, outerBounds.bottom) - 1f
                if (!visible) continue
                val labels = mutableListOf<String>()
                fun textOf(current: SemanticsNode) {
                    current.config.getOrNull(SemanticsProperties.Text)?.forEach { labels += it.text }
                    current.children.forEach(::textOf)
                }
                textOf(scroll)
                val key = labels.joinToString("|").ifBlank { "node-${target.id}" }
                if (!seenHorizontal.add(key)) continue
                reset(target)
                horizontalNumber++
                var ended = false
                for (page in 1..120) {
                    val current = axis(target)
                    if (current.value() >= current.maxValue() - epsilon) {
                        ended = true
                        break
                    }
                    val beforeValue = current.value()
                    val step = node(target).fetchSemanticsNode().boundsInRoot.width * 0.65f
                    check(step > 0f) { "Scroll has zero size: $name / horizontal $horizontalNumber" }
                    node(target).performSemanticsAction(SemanticsActions.ScrollBy) { it(step, 0f) }
                    frame("_rolagemh${horizontalNumber}_$page")
                    val advanced = axis(target)
                    check(advanced.value() > beforeValue + epsilon || advanced.value() >= advanced.maxValue() - epsilon) {
                        "Horizontal scroll did not advance: $name / $horizontalNumber / $page / before=$beforeValue / after=${advanced.value()} / max=${advanced.maxValue()}"
                    }
                }
                if (axis(target).value() >= axis(target).maxValue() - epsilon) ended = true
                check(ended) { "Incomplete horizontal scroll after 120 steps: $name / $horizontalNumber" }
                reset(target)
            }
        }
        sweepVisibleHorizontal()
        val ordered = discover().filter { it.vertical }.sortedBy { it.area }
        for (target in ordered) {
            var ended = false
            for (page in 1..120) {
                val current = axis(target)
                if (current.value() >= current.maxValue() - epsilon) {
                    ended = true
                    break
                }
                val beforeValue = current.value()
                val bounds = node(target).fetchSemanticsNode().boundsInRoot
                val step = bounds.height * 0.65f
                check(step > 0f) { "Scroll has zero size: $name / ${target.order}" }
                node(target).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
                frame("_rolagem${target.order + 1}_v_$page")
                sweepVisibleHorizontal()
                val advanced = axis(target)
                check(advanced.value() > beforeValue + epsilon || advanced.value() >= advanced.maxValue() - epsilon) {
                    "Scroll did not advance: $name / ${target.order} / $page / before=$beforeValue / after=${advanced.value()} / max=${advanced.maxValue()}"
                }
            }
            val final = axis(target)
            if (final.value() >= final.maxValue() - epsilon) ended = true
            check(ended) { "Incomplete scroll after 120 steps: $name / ${target.order}" }
            reset(target)
        }
        // Return to the start; scroll-axis values are not necessarily pixel offsets in lazy lists.
        compose.waitForIdle()
    }
}
