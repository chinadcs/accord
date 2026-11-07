package app.accord.core.analysis

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyReviewSessionTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private val oct22 = Instant.parse("2026-10-22T15:00:00Z") // quinta-feira, 12h em São Paulo
    private val oct26 = Instant.parse("2026-10-26T03:00:00Z") // segunda-feira 00h em São Paulo
    private val food = ReviewPoint(ReviewPoint.Kind.Category, "food")
    private val pending = ReviewPoint(ReviewPoint.Kind.Pending, "e:abc")
    private val rent = ReviewPoint(ReviewPoint.Kind.Commitment, "serie-1:3")

    @Test
    fun `nova revisão em 22-10 abre 12 a 18 e em 26-10 abre 19 a 25`() {
        assertEquals(ReviewPeriod(d(10, 12), d(10, 19)), WeeklyReviewSession.start(oct22).period)
        assertEquals(ReviewPeriod(d(10, 19), d(10, 26)), WeeklyReviewSession.start(oct26).period)
        assertTrue(WeeklyReviewSession.start(oct22).visited.isEmpty())
        assertEquals(ReviewStatus.InProgress, WeeklyReviewSession.start(oct22).status)
    }

    @Test
    fun `visitar acumula progresso de leitura e pausar mantém período e progresso`() {
        val paused = WeeklyReviewSession.start(oct22).visit(food).visit(pending).pause()
        assertEquals(ReviewStatus.Paused, paused.status)
        assertEquals(setOf(food, pending), paused.visited)
        assertEquals(ReviewPeriod(d(10, 12), d(10, 19)), paused.period)
        // Retomar volta a andamento com o mesmo período e o mesmo progresso.
        val resumed = paused.resume()
        assertEquals(ReviewStatus.InProgress, resumed.status)
        assertEquals(setOf(food, pending), resumed.visited)
    }

    @Test
    fun `pausada ignora visitas até ser retomada`() {
        val paused = WeeklyReviewSession.start(oct22).pause()
        assertEquals(emptySet(), paused.visit(food).visited)
        assertEquals(setOf(food), paused.resume().visit(food).visited)
    }

    @Test
    fun `alterar o período zera o progresso e uma seleção manual não volta ao padrão sozinha`() {
        val earlier = ReviewPeriod(d(10, 5), d(10, 12))
        val changed = WeeklyReviewSession.start(oct22).visit(food).changePeriod(earlier)
        assertEquals(earlier, changed.period)
        assertEquals(emptySet(), changed.visited)
        // Escolher o mesmo período não apaga o progresso.
        val same = WeeklyReviewSession.start(oct22).visit(food)
        assertEquals(same, same.changePeriod(same.period))
        // Voltar dos detalhes e retomar preservam a seleção manual: nada aqui consulta o relógio.
        assertEquals(earlier, changed.pause().resume().period)
    }

    @Test
    fun `próximo ponto é o primeiro não visitado na ordem recebida`() {
        val points = listOf(food, pending, rent)
        val s = WeeklyReviewSession.start(oct22)
        assertEquals(food, s.nextUnvisited(points))
        assertEquals(pending, s.visit(food).nextUnvisited(points))
        assertNull(s.visitAll(points).nextUnvisited(points))
        assertNull(s.nextUnvisited(emptyList()))
    }

    @Test
    fun `pontos que sumiram não contam no progresso`() {
        val s = WeeklyReviewSession.start(oct22).visit(food).visit(pending)
        assertEquals(1, s.visitedAmong(listOf(food, rent)))
        assertEquals(2, s.visitedAmong(listOf(food, pending, rent)))
    }

    @Test
    fun `guardar e ler preserva período, instante, estado e visitados`() {
        val session = WeeklyReviewSession.start(oct22).visit(food).visit(ReviewPoint(ReviewPoint.Kind.Commitment, "serie-1:3")).pause()
        assertEquals(session, WeeklyReviewCodec.decode(WeeklyReviewCodec.encode(session)))
    }

    @Test
    fun `identidade com dois-pontos e cerquilha sobrevive ao guardar`() {
        val ids = listOf("serie:3", "compra#2", "e:abc:def")
        val session = ids.fold(WeeklyReviewSession.start(oct22)) { s, id -> s.visit(ReviewPoint(ReviewPoint.Kind.Pending, id)) }
        val back = WeeklyReviewCodec.decode(WeeklyReviewCodec.encode(session))!!
        assertEquals(ids.map { ReviewPoint(ReviewPoint.Kind.Pending, it) }.toSet(), back.visited)
    }

    @Test
    fun `dado ausente, corrompido ou de formato desconhecido vira sem revisão`() {
        assertNull(WeeklyReviewCodec.decode(null))
        assertNull(WeeklyReviewCodec.decode(""))
        assertNull(WeeklyReviewCodec.decode("não é json"))
        assertNull(WeeklyReviewCodec.decode("""{"start":"2026-10-12"}"""))
        assertNull(WeeklyReviewCodec.decode("""{"start":"x","endExclusive":"y","startedAtMillis":1,"status":"InProgress","visited":[]}"""))
        assertNull(WeeklyReviewCodec.decode("""{"start":"2026-10-12","endExclusive":"2026-10-19","startedAtMillis":1,"status":"Outro","visited":[]}"""))
    }

    @Test
    fun `ponto ilegível é descartado sem perder os demais`() {
        val text = """{"start":"2026-10-12","endExclusive":"2026-10-19","startedAtMillis":1,"status":"Paused","visited":["Category:food","lixo","Outro:x"]}"""
        assertEquals(setOf(food), WeeklyReviewCodec.decode(text)!!.visited)
    }
}
