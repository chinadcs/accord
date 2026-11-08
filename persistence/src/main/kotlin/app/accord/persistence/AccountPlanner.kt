package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfigureCard
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EditAccount
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Session
import app.accord.core.space.SetMyDefaultAccount
import java.util.UUID

/** Contas, conta padrão de cada pessoa e configuração do cartão do espaço. */
internal class AccountPlanner(private val dao: SpaceDao) {
    /** Uma única conta já é o padrão; com várias, respeita a escolha individual persistida. */
    suspend fun effectiveDefault(session: Session): String? =
        dao.preferences(DeterministicIds.preferences(session.spaceId, session.personId))?.defaultAccountId
            ?: dao.accounts().singleOrNull()?.id

    /** Conta de um pagamento ou recebimento: a informada ou a padrão do autor; obrigatória. */
    suspend fun resolveRequired(session: Session, given: String?, errors: MutableList<FieldError>): String? {
        val chosen = given
            ?: effectiveDefault(session)
        return when {
            chosen == null -> {
                errors += FieldError("accountId", ErrorCode.Required, "Escolha a conta; você ainda não definiu uma conta padrão.")
                null
            }
            dao.account(chosen) == null -> {
                errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
                null
            }
            else -> chosen
        }
    }

    private fun bankName(bank: BankRef) = when (bank) {
        is BankRef.Catalog -> bank.name
        is BankRef.Other -> bank.name
    }.trim()

    suspend fun planAdd(c: AddAccount): Planned {
        val nickname = c.nickname.trim()
        val bankName = bankName(c.bank)
        val errors = buildList {
            if (bankName.isEmpty()) add(FieldError("bank", ErrorCode.Required, "Escolha o banco."))
            if (nickname.isEmpty()) add(FieldError("nickname", ErrorCode.Required, "Informe um apelido para a conta."))
        }
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet()) {
            val row = AccountRow(
                id = UUID.randomUUID().toString(),
                bankIspb = (c.bank as? BankRef.Catalog)?.ispb,
                bankName = bankName,
                nickname = nickname,
                revision = 1,
            )
            dao.insertAccount(row)
            listOf(EntityChange(EntityType.Account, row.id, null, 1))
        }
    }

    suspend fun planEdit(c: EditAccount): Planned {
        val account = dao.account(c.accountId) ?: return stop("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != account.revision) {
            return Planned.Stop(CommandResult.StaleVersion(account.id, account.revision))
        }
        if (c.nickname == null && c.bank == null) return stop("nickname", ErrorCode.Required, "Informe o que mudou.")
        val nickname = c.nickname?.trim()
        if (nickname != null && nickname.isEmpty()) return stop("nickname", ErrorCode.Required, "Informe um apelido para a conta.")
        val bank = c.bank
        if (bank != null && bankName(bank).isEmpty()) return stop("bank", ErrorCode.Required, "Escolha o banco.")
        // Correção de cadastro: mesma identidade; lançamentos antigos passam a exibir o banco e o apelido atuais.
        return Planned.Go(months = emptySet()) {
            val next = account.copy(
                nickname = nickname ?: account.nickname,
                bankIspb = if (bank != null) (bank as? BankRef.Catalog)?.ispb else account.bankIspb,
                bankName = if (bank != null) bankName(bank) else account.bankName,
                revision = account.revision + 1,
            )
            dao.upsertAccount(next)
            listOf(EntityChange(EntityType.Account, next.id, null, next.revision))
        }
    }

    suspend fun planSetDefault(session: Session, c: SetMyDefaultAccount): Planned {
        dao.account(c.accountId) ?: return stop("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
        return Planned.Go(months = emptySet()) {
            val id = DeterministicIds.preferences(session.spaceId, session.personId)
            val revision = (dao.preferences(id)?.revision ?: 0) + 1
            dao.upsertPreferences(PreferencesRow(id, session.personId, c.accountId, revision))
            listOf(EntityChange(EntityType.Preferences, id, null, revision))
        }
    }

    suspend fun planConfigureCard(session: Session, c: ConfigureCard): Planned {
        val bank = c.bank
        val errors = buildList {
            if (c.closingDay !in 1..31) add(FieldError("closingDay", ErrorCode.OutOfRange, "O dia de fechamento vai de 1 a 31."))
            if (c.dueDay !in 1..31) add(FieldError("dueDay", ErrorCode.OutOfRange, "O dia de vencimento vai de 1 a 31."))
            if (c.nickname.isBlank()) add(FieldError("nickname", ErrorCode.Required, "Informe um apelido para o cartão."))
            if (bank != null && bankName(bank).isEmpty()) add(FieldError("bank", ErrorCode.Required, "Informe o nome do banco."))
            if (c.creditLimit?.cents?.let { it <= 0 } == true) add(FieldError("creditLimit", ErrorCode.OutOfRange, "O limite deve ser maior que zero."))
        }
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet()) {
            // Não remapeia atribuições existentes: só as novas compras usam os dias novos.
            val id = cardIdOf(session.spaceId)
            val revision = (dao.card(id)?.revision ?: 0) + 1
            dao.upsertCard(
                CardRow(
                    id = id,
                    bankIspb = (c.bank as? BankRef.Catalog)?.ispb,
                    bankName = c.bank?.let(::bankName),
                    nickname = c.nickname.trim(),
                    closingDay = c.closingDay,
                    dueDay = c.dueDay,
                    revision = revision,
                    creditLimitCents = c.creditLimit?.cents,
                ),
            )
            listOf(EntityChange(EntityType.Card, id, null, revision))
        }
    }
}

/** Um cartão por espaço; a identidade é determinística para que todo aparelho chegue à mesma linha. */
internal fun cardIdOf(spaceId: String) = DeterministicIds.of(spaceId, "card", "main")
