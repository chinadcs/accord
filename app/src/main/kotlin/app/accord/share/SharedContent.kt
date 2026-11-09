package app.accord.share

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import app.accord.core.chat.InterpreterPrompt

/**
 * O que outro app entregou pelo "Compartilhar" do Android (accord-jcd9). Fica só em memória, até a navegação
 * levar o conteúdo ao destino: texto vira rascunho do Chat, arquivo vira leitura na importação. Nada é gravado aqui
 * e nenhuma permissão persistente é pedida: reler o arquivo exige compartilhá-lo ou selecioná-lo de novo (S4).
 */
sealed interface SharedContent {
    /** Texto já normalizado para caber numa mensagem do Chat; chega ao campo de escrita sem ser enviado. */
    data class Text(val text: String) : SharedContent

    /** Endereço de conteúdo do arquivo; a leitura e a validação do formato são as da importação. */
    data class File(val uri: Uri) : SharedContent

    companion object {
        /** Lê uma intenção ACTION_SEND; qualquer outra, ou uma sem conteúdo aproveitável, não vira nada. Arquivo tem prioridade: apps de e-mail mandam o corpo junto do anexo. */
        fun from(intent: Intent?): SharedContent? {
            if (intent?.action != Intent.ACTION_SEND) return null
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { return File(it) }
            return normalizeText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))?.let(::Text)
        }

        /**
         * Espaços e quebras de linha viram um espaço (o validador do Chat recusa caracteres de controle) e o excesso
         * é cortado no limite da mensagem; texto só de espaços não vira nada.
         */
        fun normalizeText(raw: CharSequence?): String? =
            raw?.toString()?.replace(Regex("\\s+"), " ")?.trim()?.take(InterpreterPrompt.MAX_MESSAGE)?.trimEnd()?.takeIf { it.isNotEmpty() }
    }
}
