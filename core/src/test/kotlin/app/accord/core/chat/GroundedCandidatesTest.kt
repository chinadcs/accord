package app.accord.core.chat

import kotlin.test.*

class GroundedCandidatesTest {
    @Test
    fun `vocabulario de acao distingue despesa de consulta e preserva mensagens fora do dominio`() {
        val context = ChatBattery.realisticContext()
        assertEquals(setOf("despesa", "sem_acao"), GroundedCandidates.actions("consulta veterinária 184 no débito", context, 11))
        assertEquals(setOf("consulta", "sem_acao"), GroundedCandidates.actions("quanto já gastamos no veterinário?", context, 11))
        assertEquals(setOf("sem_acao"), GroundedCandidates.actions("qual é a capital do Canadá?", context, 11))
        assertEquals(setOf("sem_acao"), GroundedCandidates.actions("boa noite", context, 11))
        assertEquals(setOf("planejar_compra", "sem_acao"), GroundedCandidates.actions("vou trocar meu notebook", context, 11))
        assertEquals(setOf("assinatura_recorrente", "sem_acao"), GroundedCandidates.actions("licença do programa 79 todo ano", context, 11))
        assertEquals(setOf("estorno", "sem_acao"), GroundedCandidates.actions("a empresa devolveu 139", context, 11))
        assertEquals(setOf("corrigir_data", "sem_acao"), GroundedCandidates.actions("era dia 14", context, 11))
        assertEquals(setOf("corrigir_categoria", "sem_acao"), GroundedCandidates.actions("muda para Lazer", context, 11))
        assertEquals(setOf("corrigir_valor", "sem_acao"), GroundedCandidates.actions("corrige o valor da consulta para 87,40", context, 11))
    }

    @Test
    fun `vocabulario de categoria prioriza finalidade composta e nao usa marca de loja`() {
        val context = ChatBattery.realisticContext()
        assertEquals(listOf("alimentacao"), SeedCategoryVocabulary.candidates(context, "água mineral")?.map { it.id })
        assertTrue(SeedCategoryVocabulary.candidates(context, "ração para gato")!!.any { it.id == "pet.alimentacao" })
        assertNull(SeedCategoryVocabulary.candidates(context, "Shopee"))
        assertEquals(listOf("suco"), SeedCategoryVocabulary.matches(context.categoryById("alimentacao")!!, "suco"))
        assertTrue(SeedCategoryVocabulary.matches(context.categoryById("alimentacao")!!.copy(name = "Outro propósito"), "suco").isEmpty())
    }

    @Test
    fun `servicos com troca ou ajuste nao viram correcao do recibo no perfil revisado`() {
        val context = ChatBattery.realisticContext()
        assertEquals(setOf("despesa", "sem_acao"), GroundedCandidates.actions("troca de óleo 189 no débito", context, 9))
        assertEquals(setOf("despesa", "sem_acao"), GroundedCandidates.actions("ajuste de calça 47 em dinheiro", context, 9))
        assertEquals(setOf("corrigir_categoria", "sem_acao"), GroundedCandidates.actions("quero trocar por Transporte", context, 9))
        assertEquals(setOf("corrigir_valor", "sem_acao"), GroundedCandidates.actions("ajuste o valor para 31,70", context, 9))
    }

    @Test
    fun `opcoes de intencao preservam recusa e distinguem consulta correcao e recebimento`() {
        val context = ChatBattery.realisticContext()
        assertEquals(setOf("consulta", "sem_acao"), GroundedCandidates.actions("quanto gastamos com assinaturas?", context))
        assertEquals(setOf("renda", "sem_acao"), GroundedCandidates.actions("recebimento de 1940 no próximo mês", context))
        assertEquals(setOf("corrigir_pagamento", "sem_acao"), GroundedCandidates.actions("ajuste para débito", context))
        assertEquals(setOf("estorno", "sem_acao"), GroundedCandidates.actions("recebi um estorno", context))
        assertEquals(setOf("despesa", "sem_acao"), GroundedCandidates.actions("fone de ouvido 149 em 3x", context))
        assertEquals(setOf("despesa", "sem_acao"), GroundedCandidates.actions("o cinema foi 44 no pix", context))
    }

    @Test
    fun `recuperacao usa descricoes atuais conserva pai e nao depende de ids do seed`() {
        val context = ChatBattery.context().copy(categories = listOf(
            CategoryItem("a", "id-1", "Animal"),
            CategoryItem("b", "id-2", "Cuidados", "id-1", "coleiras e brinquedos do animal"),
            CategoryItem("c", "id-3", "Alimentação", hint = "comida e bebida"),
        ))
        assertEquals(listOf("id-1", "id-2"), GroundedCandidates.categories(context, "coleira").map { it.id })
        assertEquals(context.categories, GroundedCandidates.categories(context, "produto não reconhecido"), "sem evidência mantém catálogo completo")
    }
}
