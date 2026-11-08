package app.accord.persistence

import app.accord.core.chat.*
import app.accord.core.space.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MultiCardChatTest : ChatTestBase() {
    @Test fun `chat usa padrao individual ou cartao citado e recibo identifica qual`() = runBlocking<Unit> {
        val e = env().apply { interpretation = RuleInterpreter() }
        val space = e.space
        val a = space.execute(e.handle.env(), ConfigureCard(BankRef.Other("Banco Azul"), "Azul", 8, 15)).applied().changes.single { it.type == EntityType.Card }.id
        val b = space.execute(e.handle.env(), ConfigureCard(BankRef.Other("Banco Verde"), "Verde", 20, 28, createNew = true)).applied().changes.single { it.type == EntityType.Card }.id
        space.execute(e.handle.env(), SetMyDefaultCard(b)).applied()
        e.say("mercado 40 no cartão")
        assertEquals(b, e.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).single().cardId)
        assertEquals("Verde", e.store.snapshot().receipts.last().summary.cardLabel)
        e.say("mercado 50 no cartão Azul")
        assertEquals(a, e.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).single { it.totalCents == 5000L }.cardId)
        assertEquals("Azul", e.store.snapshot().receipts.last().summary.cardLabel)
        assertEquals(b, space.card().value().id)
        e.say("mercado 25 no cart\u00e3o Banco Azul")
        assertEquals(a, e.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).single { it.totalCents == 2500L }.cardId)
        val count = e.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).size
        e.say("mercado 70 no cart\u00e3o Inexistente")
        assertEquals(count, e.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).size)
    }

    @Test fun `cartao ambiguo nao grava silenciosamente no padrao`() = runBlocking<Unit> {
        val e = env().apply { interpretation = RuleInterpreter() }
        e.space.execute(e.handle.env(), ConfigureCard(BankRef.Other("Banco"), "Azul", 8, 15)).applied()
        e.space.execute(e.handle.env(), ConfigureCard(BankRef.Other("Outro"), "Azul", 8, 15, createNew = true)).applied()
        e.say("mercado 50 no cartão Azul")
        assertTrue(e.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).isEmpty())
    }
}
