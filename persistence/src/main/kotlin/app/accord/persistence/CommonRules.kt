package app.accord.persistence

import app.accord.core.space.Classification
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Settlement

/** Validações compartilhadas pelos planejadores de lançamento: origem de intenção, categoria e valor de baixa. */
internal class CommonRules(private val dao: SpaceDao) {
    /**
     * Conversão de intenção (S7, ADR 0048): a intenção precisa existir e não pode ter outro compromisso, nem excluído, já vinculado neste aparelho.
     * O serviço impõe o mesmo vínculo único entre os aparelhos; aqui a recusa vem antes de gravar qualquer coisa.
     */
    suspend fun originError(intentionId: String?): FieldError? = when {
        intentionId == null -> null
        dao.intention(intentionId) == null -> FieldError("originIntentionId", ErrorCode.UnknownReference, "Intenção não encontrada.")
        dao.originLinks().any { it.intentionId == intentionId } ->
            FieldError("originIntentionId", ErrorCode.NotAllowed, "Esta intenção já foi convertida em compromisso.")
        else -> null
    }

    /** Devolve o ID da classificação (nulo é "a classificar") e acumula erro se a categoria não existe. */
    suspend fun validateClassification(classification: Classification, errors: MutableList<FieldError>): String? =
        when (classification) {
            Classification.ToClassify -> null
            is Classification.Category -> {
                if (dao.activeCategory(classification.id) == null) {
                    errors += FieldError("classification", ErrorCode.UnknownReference, "Categoria não encontrada.")
                }
                classification.id
            }
        }

    fun validateSettlement(settlement: Settlement, field: String, errors: MutableList<FieldError>) {
        val amount = settlement.amount
        if (amount != null && amount.cents <= 0) {
            errors += FieldError(field, ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        }
    }
}
