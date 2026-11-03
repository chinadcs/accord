package app.accord.persistence

import app.accord.core.space.CategoryMoveImpact
import app.accord.core.space.CommandResult
import app.accord.core.space.EntityChange
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.InstallmentView
import java.time.YearMonth

/**
 * Resultado do planejamento de um comando: valida e calcula sem gravar. A prévia usa o plano
 * como está; a execução chama [Go.commit] dentro da mesma transação que grava a operação local.
 */
internal sealed interface Planned {
    /** O comando não será aplicado: Rejeitado, Decisão necessária ou Versão desatualizada. */
    data class Stop(val result: CommandResult) : Planned

    data class Go(
        val before: List<InstallmentView> = emptyList(),
        val after: List<InstallmentView> = emptyList(),
        val months: Set<YearMonth>,
        val undoable: Boolean = false,
        /** Dado extra gravado na operação para o desfazer, como `occurrence=2`. */
        val detail: String? = null,
        /** Recorrência: ocorrências com exceção própria substituídas pela edição. */
        val replaced: List<Int> = emptyList(),
        /** Exclusão: estornos vinculados que saem junto com a compra. */
        val linkedRefundIds: List<String> = emptyList(),
        /** Limites: meses posteriores com configuração própria que a edição substitui. */
        val replacedLimitMonths: List<YearMonth> = emptyList(),
        /** Compra parcelada com nova data: a fatura que a data indicaria, oferecida sem ser aplicada. */
        val suggestedInvoice: YearMonth? = null,
        /** Mover lançamentos entre categorias: quantos e em quais meses. */
        val categoryMove: CategoryMoveImpact? = null,
        val commit: suspend () -> List<EntityChange>,
    ) : Planned
}

internal fun stop(field: String, code: ErrorCode, message: String) =
    Planned.Stop(CommandResult.Rejected(listOf(FieldError(field, code, message))))

internal fun stop(errors: List<FieldError>) = Planned.Stop(CommandResult.Rejected(errors))
