package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.ClearIconAssociation
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetIconAssociation
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** ADR 0047: escolha de ícone por lançamento ou por nome, sincronizada, só visual e sem desfazer. */
class IconAssociationTest : SpaceTestBase() {
    private val uber = IconRef(IconSource.Brand, "uber")
    private val spotify = IconRef(IconSource.Brand, "spotify")

    private suspend fun Handle.pix(description: String = "Uber *Trip"): String {
        val account = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(account)).applied()
        return register(description)
    }

    private suspend fun Handle.register(description: String) = space.execute(
        env(), RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed("transporte")), d(2026, 10, 20), brl(25)),
    ).applied().changes.single().id

    private fun CommandResult.errorCodes() = assertIs<CommandResult.Rejected>(this).errors.map { it.code }

    @Test fun `escolha por nome e comparada sem caixa, acento e espacos extras e vira entidade sincronizada`() = runBlocking<Unit> {
        val h = open()
        val receipt = h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "  UBÉR   *trip ", uber)).applied()
        val change = receipt.changes.single()
        assertEquals(EntityType.IconAssociation, change.type)
        assertFalse(receipt.undoable)
        val view = h.space.iconAssociations().value().single()
        assertEquals(change.id, view.id)
        assertEquals(IconScope.Name, view.scope)
        assertEquals("uber *trip", view.target)
        assertEquals(uber, view.icon)
        assertEquals(1, view.revision)
    }

    @Test fun `a mesma regra nos dois aparelhos do espaco tem a mesma identidade`() = runBlocking<Unit> {
        val a = open("ana")
        val b = open("bia")
        val idA = a.space.execute(a.env(), SetIconAssociation(IconScope.Name, "Spotify", spotify)).applied().changes.single().id
        val idB = b.space.execute(b.env(), SetIconAssociation(IconScope.Name, "SPOTIFY", spotify)).applied().changes.single().id
        assertEquals(idA, idB)
        val other = a.space.execute(a.env(), SetIconAssociation(IconScope.Name, "Netflix", IconRef(IconSource.Brand, "netflix"))).applied().changes.single().id
        assertNotEquals(idA, other)
    }

    @Test fun `escolha de um lancamento exige que ele exista e nao altera o lancamento`() = runBlocking<Unit> {
        val h = open()
        val id = h.pix()
        val before = h.space.entry(id).value()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Entry, id, spotify)).applied()
        assertEquals(before, h.space.entry(id).value(), "ícone é só visual")
        val view = h.space.iconAssociations().value().single()
        assertEquals(IconScope.Entry, view.scope)
        assertEquals(id, view.target)

        val missing = h.space.execute(h.env(), SetIconAssociation(IconScope.Entry, "nao-existe", spotify))
        assertEquals(listOf(ErrorCode.UnknownReference), missing.errorCodes())
        assertEquals(1, h.space.iconAssociations().value().size, "nada gravado na recusa")
    }

    @Test fun `chave fora do formato do catalogo ou alvo vazio e recusado sem gravar`() = runBlocking<Unit> {
        val h = open()
        val bad = listOf(
            IconRef(IconSource.Brand, "https://evil.example/x.png"), IconRef(IconSource.Brand, "Uber"), IconRef(IconSource.Brand, "../uber"),
            IconRef(IconSource.Brand, ""), IconRef(IconSource.Bank, "123"), IconRef(IconSource.Bank, "6070119a"), IconRef(IconSource.Generic, "Shopping Bag"),
        )
        for (icon in bad) {
            assertEquals(listOf(ErrorCode.NotAllowed), h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "Uber", icon)).errorCodes(), icon.toString())
        }
        assertEquals(listOf(ErrorCode.Required), h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "   ", uber)).errorCodes())
        assertEquals(listOf(ErrorCode.OutOfRange), h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "x".repeat(201), uber)).errorCodes())
        assertTrue(h.space.iconAssociations().value().isEmpty())
        // As três origens do formato valem.
        for (icon in listOf(uber, IconRef(IconSource.Bank, "60701190"), IconRef(IconSource.Generic, "shopping-bag"))) {
            h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "Loja ${icon.source}", icon)).applied()
        }
        assertEquals(3, h.space.iconAssociations().value().size)
    }

    @Test fun `escolher de novo substitui, respeita a versao esperada e remover volta ao automatico`() = runBlocking<Unit> {
        val h = open()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "Uber", uber)).applied()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "uber", spotify, expectedRevision = 1)).applied()
        val view = h.space.iconAssociations().value().single()
        assertEquals(spotify, view.icon)
        assertEquals(2, view.revision)

        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "uber", uber, expectedRevision = 1)))
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), ClearIconAssociation(IconScope.Name, "uber", expectedRevision = 1)))
        assertEquals(spotify, h.space.iconAssociations().value().single().icon, "versão velha não grava")

        val cleared = h.space.execute(h.env(), ClearIconAssociation(IconScope.Name, "UBER")).applied()
        assertEquals(EntityType.IconAssociation, cleared.changes.single().type)
        assertTrue(h.space.iconAssociations().value().isEmpty())
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), ClearIconAssociation(IconScope.Name, "uber")).errorCodes())

        // Escolher depois de remover reaproveita a identidade e avança a versão.
        h.space.execute(h.env(), SetIconAssociation(IconScope.Name, "Uber", uber)).applied()
        val again = h.space.iconAssociations().value().single()
        assertEquals(view.id, again.id)
        assertEquals(4, again.revision)
    }

    @Test fun `escolher ou limpar icone nao mexe no orcamento`() = runBlocking<Unit> {
        val h = open()
        val id = h.pix()
        val month = ym(2026, 10)
        val budget = h.space.budget(month).value()
        h.space.execute(h.env(), SetIconAssociation(IconScope.Entry, id, uber)).applied()
        h.space.execute(h.env(), ClearIconAssociation(IconScope.Entry, id)).applied()
        assertEquals(budget, h.space.budget(month).value())
        assertTrue(h.space.iconAssociations().value().isEmpty())
    }
}
