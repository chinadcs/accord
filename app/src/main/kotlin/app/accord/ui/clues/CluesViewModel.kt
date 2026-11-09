package app.accord.ui.clues

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.clues.*
import app.accord.core.space.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

data class ClueItem(val key: String, val fingerprint: String, val snapshot: ClueSnapshot,
    val current: Clue?, val evaluations: List<ClueEvaluation>) {
    val evaluated: Boolean get() = current == null || evaluations.any { it.fingerprint == fingerprint }
}

data class CluesUiState(
    val loading: Boolean = true, val failed: Boolean = false, val saving: Boolean = false,
    val items: List<ClueItem> = emptyList(), val coverage: List<ClueCoverage> = emptyList(),
    val coverageAdequate: Boolean = false, val categories: Map<String, CategoryNode> = emptyMap(),
    val selected: ClueItem? = null, val seen: ClueSnapshot? = null, val seenFingerprint: String? = null,
    val changed: Boolean = false, val details: Map<String, EntryDetail?> = emptyMap(),
    val origins: Map<String, List<ClueImportOrigin>> = emptyMap(), val saveFailed: Boolean = false,
    val reference: LocalDate = LocalDate.MIN,
    val memberNames: Map<String, String> = emptyMap(),
    val hasImportPending: Boolean = false,
) {
    val evidenceAvailable: Boolean get() = seen != null && seen.evidence.all { details[it.id]?.voided == false }
}

/** Queries and detector run off the UI thread. Resume never applies or repeats a financial correction. */
class CluesViewModel(private val provider: SpaceProvider, private val saved: SavedStateHandle) : ViewModel() {
    private val reference = saved.get<String>("reference")?.let { LocalDate.ofEpochDay(it.toLong()) } ?: provider.clock.today()
    private val selectedKey = saved.get<String>("clueKey")
    private val _state = MutableStateFlow(CluesUiState(reference = reference))
    val state = _state.asStateFlow()
    private var job: Job? = null
    init { saved["reference"] = reference.toEpochDay().toString(); refresh() }

    fun refresh() {
        if (_state.value.saving) return
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val space = provider.space()
                    val source = space.clueSource(reference.minusDays(56), reference).data()
                    val analysis = ClueDetector.analyze(source, reference)
                    val evaluations = space.clueEvaluations().data().groupBy { it.key }
                    val current = analysis.clues.associateBy { it.key }
                    val items = (current.keys + evaluations.keys).sorted().map { key ->
                        val c = current[key]
                        val history = evaluations[key].orEmpty()
                        ClueItem(key, c?.fingerprint ?: history.first().fingerprint,
                            c?.snapshot() ?: history.first().snapshot, c, history)
                    }
                    val selected = items.firstOrNull { it.key == selectedKey }
                    val stored = saved.get<String>("seen")?.let { Json.decodeFromString(ClueSnapshot.serializer(), it) }
                    val seen = stored ?: selected?.snapshot
                    val seenFingerprint = saved.get<String>("seenFingerprint") ?: selected?.fingerprint
                    val details = seen?.evidence.orEmpty().associate { it.id to space.entry(it.id).data() }
                    val origins = seen?.evidence.orEmpty().associate { it.id to space.clueImportOrigins(it.id).data() }
                    CluesUiState(loading = false, items = items, coverage = source.coverage,
                        coverageAdequate = analysis.frequencyCoverageAdequate,
                        categories = space.categories().data().associateBy { it.id }, selected = selected,
                        seen = seen, seenFingerprint = seenFingerprint,
                        changed = selected?.current != null && seenFingerprint != selected.fingerprint,
                        details = details, origins = origins, reference = reference, memberNames = provider.memberNames(),
                        hasImportPending = source.evidence.any { it.importPending && it.entry.id in seen?.evidence.orEmpty().map { e -> e.id } },
                        saveFailed = _state.value.saveFailed)
                }
                if (loaded.seen != null) {
                    saved["seen"] = Json.encodeToString(ClueSnapshot.serializer(), loaded.seen)
                    saved["seenFingerprint"] = loaded.seenFingerprint
                }
                _state.value = loaded
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun updateAnalysis() {
        saved.remove<String>("dismissOperation")
        saved.remove<String>("seen")
        saved.remove<String>("seenFingerprint")
        refresh()
    }

    fun dismiss() {
        val s = _state.value
        val selected = s.selected ?: return
        if (s.saving || s.failed || s.changed || !s.evidenceAvailable || selected.current == null || selected.evaluated) return
        job?.cancel()
        _state.update { it.copy(saving = true, saveFailed = false) }
        val operation = saved.get<String>("dismissOperation") ?: UUID.randomUUID().toString().also { saved["dismissOperation"] = it }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { provider.space().execute(Envelope(CommandId(operation)),
                    DismissClue(selected.key, selected.fingerprint, reference)) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            _state.update { it.copy(saving = false, saveFailed = result !is CommandResult.Applied && result !is CommandResult.AlreadyApplied) }
            if (result is CommandResult.Applied || result is CommandResult.AlreadyApplied) {
                saved.remove<String>("dismissOperation")
                provider.feedback.notifyChanged()
            }
            // Re-read canonical data; a refused stale fingerprint is never treated as a saved evaluation.
            if (result != null) refresh()
        }
    }

    private fun <T> QueryResult<T>.data(): T = when (this) {
        is QueryResult.Data -> value
        is QueryResult.Failure -> throw IllegalStateException("Consulta indisponível")
    }
}
