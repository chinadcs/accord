package app.accord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.accord.core.analysis.ReviewPoint
import app.accord.core.analysis.ReviewStatus
import app.accord.core.analysis.WeeklyReviewSession
import app.accord.ui.weekly.PrefsWeeklyReviewStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** A revisão em andamento fica no aparelho, por pessoa, e sobrevive a recriar o repositório (como ao reabrir o app). */
class WeeklyReviewStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val session = WeeklyReviewSession.start(Instant.parse("2026-10-22T15:00:00Z"))
        .visit(ReviewPoint(ReviewPoint.Kind.Category, "food"))
        .visit(ReviewPoint(ReviewPoint.Kind.Commitment, "s1:3"))
        .pause()

    @Before
    fun clean() = clear()

    @After
    fun cleanUp() = clear()

    private fun clear() {
        context.getSharedPreferences("accord_weekly_review", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun semNadaGuardadoNaoHaRevisaoEmAndamento() {
        assertNull(PrefsWeeklyReviewStore(context, "ana").load())
    }

    @Test
    fun guardarELerPreservaPeriodoProgressoEEstadoAoRecriarORepositorio() {
        PrefsWeeklyReviewStore(context, "ana").save(session)
        val back = PrefsWeeklyReviewStore(context, "ana").load()
        assertEquals(session, back)
        assertEquals(ReviewStatus.Paused, back!!.status)
    }

    @Test
    fun cadaPessoaTemASuaRevisao() {
        PrefsWeeklyReviewStore(context, "ana").save(session)
        assertNull(PrefsWeeklyReviewStore(context, "bia").load())
    }

    @Test
    fun dadoCorrompidoViraSemRevisaoEmAndamento() {
        context.getSharedPreferences("accord_weekly_review", Context.MODE_PRIVATE).edit().putString("review.ana", "lixo").commit()
        assertNull(PrefsWeeklyReviewStore(context, "ana").load())
    }
}
