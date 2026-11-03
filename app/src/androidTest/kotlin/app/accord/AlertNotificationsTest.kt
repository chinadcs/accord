package app.accord

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.alerts.AlertDispatcher
import app.accord.alerts.AndroidNotifications
import app.accord.core.BudgetClock
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.alerts.AlertKind
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.AlertSnapshot
import app.accord.core.alerts.DisplayState
import app.accord.core.alerts.NotificationAccess
import app.accord.core.space.AlertNotice
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DefineLimits
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.Session
import app.accord.persistence.AccordDatabase
import app.accord.persistence.AccordDatabase_Impl
import app.accord.persistence.RoomFinanceSpace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Adaptador de notificações do Android (S5.5, ADR 0044) no emulador: canal, situação real, versão pública sem valores e o despachante sobre uma fachada real
 * em memória. Dados fictícios.
 */
@RunWith(AndroidJUnit4::class)
class AlertNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifications = AndroidNotifications(context)
    private var database: AccordDatabase? = null

    @Before
    fun prepare() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        notifications.ensureChannel()
        manager.cancelAll()
    }

    @After
    fun cleanup() {
        manager.cancelAll()
        database?.close()
    }

    private fun notice(id: String, kind: AlertKind = AlertKind.PossibleExcess) = AlertNotice(
        id, "cat", kind, LocalDate.of(2026, 10, 19), AlertSnapshot(98_000, 32_000, 130_000, 120_000, 10_000, emptyList()),
    )

    private fun active(tag: String) = manager.activeNotifications.firstOrNull { it.tag == tag }

    @Test fun oCanalDePossivelExcessoExisteEEstaAtivo() {
        val channel = manager.getNotificationChannel(AndroidNotifications.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("Possível excesso", channel.name.toString())
        assertTrue(channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    @Test fun comAPermissaoOSistemaDizConcedida() {
        assertEquals(NotificationAccess.Granted, notifications.access())
        assertEquals(AndroidNotifications.PermissionStep.AlreadyAllowed, notifications.nextStep(null))
    }

    @Test fun oAvisoLevaOTextoCompletoEAVersaoPublicaNaoLevaCategoriaNemValor() {
        notifications.show(notice("evento-1"), "Alimentação")
        val posted = active("evento-1")!!.notification
        val extras = posted.extras
        assertEquals("Alimentação: pode passar do limite", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("R$"))
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        val public = posted.publicVersion!!.extras
        val publicText = "${public.getCharSequence(Notification.EXTRA_TITLE)} ${public.getCharSequence(Notification.EXTRA_TEXT)}"
        assertFalse(publicText.contains("Alimentação"))
        assertFalse(publicText.contains("R$"))
        assertEquals(AndroidNotifications.CHANNEL_ID, posted.channelId)
    }

    @Test fun excessoJaOcorridoTemTextoProprio() {
        notifications.show(notice("evento-2", AlertKind.AlreadyExceeded), "Casa")
        val title = active("evento-2")!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertEquals("Casa: já passou do limite", title)
    }

    @Test fun publicarDuasVezesOMesmoEventoSubstituiENaoDuplica() {
        notifications.show(notice("evento-3"), "Alimentação")
        notifications.show(notice("evento-3"), "Alimentação")
        assertEquals(1, manager.activeNotifications.count { it.tag == "evento-3" })
    }

    // ---- despachante sobre uma fachada real em memória ----

    private val food get() = DeterministicIds.seedCategory("space-1", "alimentacao")
    private val groceries get() = DeterministicIds.seedCategory("space-1", "alimentacao.mercado")

    private fun space(): RoomFinanceSpace = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<AccordDatabase>(context, factory = { AccordDatabase_Impl() }).setDriver(BundledSQLiteDriver()).build()
        database = db
        val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneOffset.UTC))
        RoomFinanceSpace.open(db, clock, { Session("space-1", "ana") })
    }

    private suspend fun RoomFinanceSpace.exceed() {
        execute(
            Envelope(CommandId("limite")),
            DefineLimits(YearMonth.of(2026, 10), LimitScope.FromThisMonth, listOf(CategoryLimitEdit(food, LimitEdit(total = TotalChange.Set(Money(120_000)))))),
        )
        val result = execute(
            Envelope(CommandId("gasto")),
            RegisterDirectPayment(DirectPaymentKind.Cash, "gasto", Classification.Category(groceries), LocalDate.of(2026, 10, 20), Money(130_000)),
        )
        assertTrue(result is CommandResult.Applied)
    }

    @Test fun oDespachantePublicaOAvisoPendenteUmaVezEConfirmaAExibicao() = runBlocking<Unit> {
        val space = space()
        val dispatcher = AlertDispatcher(notifications) { space }
        space.setAlertPreference(AlertPreference.Enabled)
        space.onAlertRecorded = { dispatcher.dispatch() }
        space.exceed()

        val status = (space.alertStatuses() as QueryResult.Data).value.single()
        assertEquals(DisplayState.Shown, status.display)
        val posted = active(status.id)
        assertNotNull("a notificação saiu com a chave do evento como etiqueta", posted)
        assertEquals("Alimentação: já passou do limite", posted!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        dispatcher.dispatch()
        assertEquals("chamar de novo não repete", 1, manager.activeNotifications.count { it.tag == status.id })
    }

    @Test fun preferenciaDesativadaOuNaoDefinidaSuprimeESemNotificacaoRetroativa() = runBlocking<Unit> {
        val space = space()
        val dispatcher = AlertDispatcher(notifications) { space }
        space.setAlertPreference(AlertPreference.Disabled)
        space.onAlertRecorded = { dispatcher.dispatch() }
        space.exceed()

        val status = (space.alertStatuses() as QueryResult.Data).value.single()
        assertEquals(DisplayState.SuppressedPreference, status.display)
        assertEquals(0, manager.activeNotifications.count { it.tag == status.id })
        // Ativar depois não exibe o que foi suprimido.
        space.setAlertPreference(AlertPreference.Enabled)
        dispatcher.dispatch()
        assertEquals(0, manager.activeNotifications.count { it.tag == status.id })
    }
}
