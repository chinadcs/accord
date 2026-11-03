package app.accord.core.chat

/** Experimental description evidence for v7/v9. Only explicit syntax marks peripheral context.
 * Complete contiguous phrases are offered, never their nested suffixes. No oracle/model confidence.
 */
internal class PurposeDescriptions(request:InterpretRequest,refinedPeople:Boolean=false,refinedVocabulary:Boolean=false,refinedProcedure:Boolean=false,refinedPlaces:Boolean=false,refinedNatural:Boolean=false,refinedForms:Boolean=false,refinedEveryday:Boolean=false) {
    // Validator 21: possessives and the clitic "me" frame an item but are not part of it ("meu salário", "minha mãe me").
    private val activeEdges=if(refinedEveryday)edges+setOf("meu","minha","meus","minhas","seu","sua","nosso","nossa","nossos","nossas","me") else edges
    private val text=request.text
    private val peripheral=mutableListOf<IntRange>()
    private val blocked=mutableListOf<IntRange>()
    private val overlong=mutableListOf<IntRange>()
    private data class Phrase(val value:String,val range:IntRange)
    private val phrases:List<Phrase>
    private val placeDropped=mutableListOf<Triple<Int,Int,String>>()
    val candidates:List<String> get()=phrases.map { it.value }.distinct()
    var oversized=false
        private set

    init {
        val spans=InterpreterGrammar.Spans(text)
        fun mark(pattern:Regex,metadata:Boolean=false) {
            pattern.findAll(text).forEach {
                blocked+=it.range
                if(metadata)peripheral+=it.range
            }
        }
        fun literal(value:String,metadata:Boolean=true) {
            mark(Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(value)}(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE),metadata)
        }
        (spans.dates.values.flatten()+spans.installments).forEach { literal(it) }
        mark(Regex("(?<![\\p{L}\\p{N}_])\\d[\\d.,]*(?![\\p{L}\\p{N}_])"),true)
        mark(Regex("\\bdinheiro\\s+vivo\\b|r\\$",RegexOption.IGNORE_CASE),true)
        request.context.categories.forEach { category->
            mark(Regex("\\bcategoria\\s+${literalPattern(category.name)}(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE),true)
        }
        request.context.accounts.forEach { account->
            val bank=literalPattern(account.label.substringBefore('·').trim())
            // Bare bank names require a method, an amount-origin frame, or an origin-subject frame.
            // 'logo do Nubank' is still part of an item, not an account clause.
            mark(Regex("\\b(?:pix|d[eé]bito)\\s+(?:(?:do|da|no|na|pelo|pela|via)\\s+)?$bank(?![\\p{L}\\p{N}_])|(?<![\\p{L}\\p{N}_])\\d[\\d.,]*\\s+(?:no|na|pelo|pela|via)\\s+$bank(?![\\p{L}\\p{N}_])|^(?:do|da|no|na)\\s+$bank\\s+(?:saiu|entrou|recebi|debitei)\\b",RegexOption.IGNORE_CASE),true)
            account.label.split('·').map(String::trim).filter(String::isNotBlank).forEach { name->
                mark(Regex("\\b(?:conta|banco)\\s+(?:(?:do|da|de)\\s+)?${literalPattern(name)}(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE),true)
            }
        }
        request.context.members.forEach { person->
            mark(Regex("(?<![\\p{L}\\p{N}_])${literalPattern(person.name)}(?=\\s+(?:pagou|gastou|comprou${if(refinedVocabulary)"|recebeu" else ""})\\b)",RegexOption.IGNORE_CASE),true)
        }
        // Validator 10 / profile 8: a separate clause like ', para o Davi' or ', com a Bia' names a recipient or companion, not the item.
        // Attached to the item ('presente para Luiza') the name stays part of the description.
        if(refinedPeople)request.context.members.forEach { person->
            mark(Regex("(?:(?<=^)|(?<=[,;:.]\\s{0,3})|(?<=\\d\\s{1,3})|(?<=(?:pix|dinheiro|cart[aã]o|cr[eé]dito|d[eé]bito)\\s{1,3}))(?:para|pra|pro|ao|com|dos|das|do|da|de)\\s+(?:(?:o|a)\\s+)?${literalPattern(person.name)}(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE),true)
        }
        mark(Regex("\\b(?:me\\s+(?:rendeu|pagaram|pagou|depositaram)|(?:o\\s+)?cliente\\s+pagou)\\b",RegexOption.IGNORE_CASE))
        // 'a cafeteria cobrou 16 pelo expresso': the subject charging or paying is the counterparty, never the item.
        // Only with an explicit 'pelo/pela' purpose after the verb, so a lone subject stays the single description.
        counterparty.find(text)?.groups?.get(1)?.takeIf { it.value.trim().split(Regex("\\s+")).size<=6 }?.let {
            blocked+=it.range
            peripheral+=it.range
        }
        mark(Regex("\\bque\\s+(?:vendi|vendemos|comprei|compramos)\\b|\\b(?:compra|custo|preço|preco|valor)\\s+(?:do|da|de)\\b",RegexOption.IGNORE_CASE))
        // Validator 18: "curso que dei", "curso que ela deu" say what the person did, not what the item is.
        if(refinedNatural)mark(Regex("\\bque\\s+(?:(?:ela|ele|eu|nos|nós)\\s+)?(?:dei|deu|demos|fiz|fez|fizemos)\\b",RegexOption.IGNORE_CASE),true)
        // Validator 21: relative dates and parts of the day say when, not what.
        if(refinedEveryday)mark(Regex("\\b(?:semana|m[eê]s|ano)\\s+(?:passad[oa]|que\\s+vem|anterior)\\b|\\b(?:fim|final)\\s+de\\s+semana\\b|\\b(?:de\\s+manh[aã]|de\\s+tarde|[àa]\\s+noite|de\\s+madrugada)\\b",RegexOption.IGNORE_CASE),true)
        // Validator 14 / profile 11: how the purchase was split is payment context, never the item. The participle is marked only
        // beside an installment count or the credit/card, so 'mesa dividida em 2 partes' keeps its noun; first-person verbs always.
        if(refinedProcedure) {
            val count="\\d{1,3}\\s*(?:x|vezes|parcelas?)(?![\\p{L}\\p{N}_])"
            val split="(?<![\\p{L}\\p{N}_])(?:dividid[oa]s?|parcelad[oa]s?)"
            mark(Regex("$split(?:\\s+em)?(?=\\s+$count)|$split(?=\\s+(?:no|na)\\s+(?:cr[eé]dito|cart[aã]o)(?![\\p{L}\\p{N}_]))|(?<![\\p{L}\\p{N}_])(?:parcelei|parcelamos|parcelou|dividi|dividimos|dividiu)(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE),true)
            if(spans.installments.isNotEmpty())mark(Regex("(?<![\\p{L}\\p{N}_])sem\\s+juros(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE),true)
        }
        val tokens=Regex("[\\p{L}\\p{N}]+(?:[-'][\\p{L}\\p{N}]+)*").findAll(text).toList()
        tokens.forEach { token->
            if(ChatText.fold(token.value) in structural || (refinedPlaces && ChatText.fold(token.value) in incomeVerbs) || (refinedForms && ChatText.fold(token.value) in receivedVerbs) || (refinedVocabulary && ChatText.fold(token.value) in receipts) || ChatPayment.entries.any { ChatText.mentionsPayment(token.value,it) }) {
                blocked+=token.range
                if(ChatPayment.entries.any { ChatText.mentionsPayment(token.value,it) })peripheral+=token.range
            }
        }
        val found=mutableListOf<Phrase>()
        val chunk=mutableListOf<MatchResult>()
        fun flush() {
            if(chunk.isEmpty())return
            var start=chunk.first().range.first
            var end=chunk.last().range.last
            val value=text.substring(start,end+1)
            // Validator 16 / profile 13: a bare place after no/na ("na padaria da esquina") says where, never what; the item stays.
            if(refinedPlaces && (if(refinedNatural)placeContextNatural else placeContext).matches(value)) {
                // Validator 18: a place that is the only phrase stays the description; otherwise it is context.
                if(refinedNatural)placeDropped+=Triple(start,end,value) else peripheral+=start..end
                chunk.clear()
                return
            }
            // A place before an explicit purpose is context: 'na oficina pelo reparo da fivela'.
            val purpose=Regex("^(?:no|na)\\s+.+?\\s+(?:pelo|pela)\\s+(.+)$",RegexOption.IGNORE_CASE).find(value)
                ?: (if(refinedNatural)Regex("^(?:do|da|de|dos|das)\\s+.+?\\s+(?:pelo|pela|pelos|pelas)\\s+(.+)$",RegexOption.IGNORE_CASE).find(value) else null)
                ?: Regex("^(?:no|na)\\s+[\\p{L}]+\\s+(?:um|uma)\\s+(.+)$",RegexOption.IGNORE_CASE).find(value)
            if(purpose!=null) {
                val previousStart=start
                start+=purpose.groups[1]!!.range.first
                peripheral+=previousStart until start
            }
            val inside=chunk.filter { it.range.first>=start }.toMutableList()
            while(inside.isNotEmpty() && ChatText.fold(inside.first().value) in activeEdges)inside.removeAt(0)
            while(inside.isNotEmpty() && ChatText.fold(inside.last().value) in activeEdges)inside.removeAt(inside.lastIndex)
            if(inside.isNotEmpty()) {
                start=inside.first().range.first
                end=inside.last().range.last
                if(end-start+1>40 || inside.size>6) {
                    // Validator 21: "conserto do notebook com o técnico João" keeps the item before the companion.
                    val cut=if(refinedEveryday)inside.indexOfFirst { it!==inside.first() && ChatText.fold(it.value) in splitters } else -1
                    val head=if(cut>=1)inside.subList(0,cut) else emptyList()
                    if(head.isNotEmpty() && head.size<=6 && head.last().range.last-start+1<=40) {
                        found+=Phrase(text.substring(start,head.last().range.last+1),start..head.last().range.last)
                        peripheral+=(head.last().range.last+1)..end
                    } else {
                        oversized=true
                        overlong+=start..end
                    }
                }
                else found+=Phrase(text.substring(start,end+1),start..end)
            }
            chunk.clear()
        }
        tokens.forEach { token->
            if(blocked.any { overlaps(it,token.range) })flush()
            else {
                if(chunk.isNotEmpty() && text.substring(chunk.last().range.last+1,token.range.first).any { !it.isWhitespace() })flush()
                chunk+=token
            }
        }
        flush()
        if(refinedNatural) {
            if(found.isEmpty())placeDropped.forEach { (start,end,value)->
                var stripped=value.replace(Regex("^(?i:no|na|num|numa|para\\s+(?:o|a)|pro|pra)\\s+"),"")
                while(true) { // a trailing connector ("mercado do bairro no" before "no pix") is not part of the place
                    val last=stripped.substringAfterLast(' ')
                    if(stripped.contains(' ') && ChatText.fold(last) in edges)stripped=stripped.dropLast(last.length).trimEnd() else break
                }
                found+=Phrase(stripped,(end-stripped.length+1)..end)
            } else placeDropped.forEach { (start,end,_)->peripheral+=start..end }
        }
        phrases=found.toList()
    }

    /** A forged proposal is still checked even when it bypasses the selector's grammar. */
    fun rejects(description:String):Boolean {
        val matches=Regex("(?<![\\p{L}\\p{N}_])${literalPattern(description)}(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE).findAll(text).toList()
        return matches.isNotEmpty() && matches.all { match->
            peripheral.any { overlaps(it,match.range) } ||
                (phrases.isNotEmpty() && blocked.any { overlaps(it,match.range) }) ||
                overlong.any { contains(it,match.range) } || phrases.any { p->
                if(!contains(p.range,match.range) || match.range==p.range)false else {
                    val prefix=text.substring(p.range.first,match.range.first).trim()
                    val quantityOnly=prefix.isNotEmpty() && prefix.split(Regex("\\s+")).all { ChatText.fold(it) in quantities }
                    // Existing proposals can omit a financial label; the v7 selector still offers the whole phrase.
                    val financialLabel=Regex("^(?:salario|renda|despesa|gasto) (?:do|da|de|com)$").matches(ChatText.fold(prefix))
                    !((quantityOnly || financialLabel) && match.range.last==p.range.last)
                }
            }
        }
    }
    private fun overlaps(a:IntRange,b:IntRange)=a.first<=b.last && b.first<=a.last
    private fun contains(a:IntRange,b:IntRange)=b.first>=a.first && b.last<=a.last
    companion object {
        private fun literalPattern(value:String):String=value.trim().split(Regex("\\s+")).joinToString("\\s+") { word->
            word.map { letter->when(ChatText.fold(letter.toString())) {
                "a"->"[aáàâãä]"; "e"->"[eéèêë]"; "i"->"[iíìîï]"; "o"->"[oóòôõö]"; "u"->"[uúùûü]"; "c"->"[cç]"
                else->Regex.escape(letter.toString())
            } }.joinToString("")
        }
        private val counterparty=Regex("^\\s*(?:(?:hoje|ontem|anteontem)\\s+)?(?:(?:o|a|os|as)\\s+)?(\\p{L}[\\p{L}'-]*(?:\\s+\\p{L}[\\p{L}'-]*)*?)\\s+(?:(?:me|nos)\\s+)?(?:pagou|pagaram|cobrou|cobraram)\\b(?=.*?(?<![\\p{L}\\p{N}_])(?:pelo|pela|pelos|pelas)\\s+\\p{L})",RegexOption.IGNORE_CASE)
        private val edges=setOf("de","do","da","dos","das","em","para","com","por","pelo","pela","no","na","o","a","os","as","um","uma","e")
        private val quantities=setOf("dois","duas","tres","quatro","cinco","seis","sete","oito","nove","dez")
        private val receipts=setOf("recebeu","receberam")
        private val incomeVerbs=setOf("caiu","cairam")
        private val receivedVerbs=setOf("deram")
        private val splitters=setOf("com","pelo","pela","pelos","pelas","para","pra","que")
        private val placeContextNatural=run {
            // Profile 15 adds the two-word "pet shop"; the shared list stays as the earlier profiles saw it.
            val place=(ReducedPreparation.places+"pet shop").joinToString("|") { literalPattern(it) }
            Regex("^(?i:no|na|num|numa|para\\s+(?:o|a)|pro|pra)\\s+(?i:$place)(?:\\s+(?i:do|da|de)\\s+\\p{L}+(?:\\s+\\p{L}+)?|(?:\\s+\\p{Lu}\\p{L}*){1,2})?$")
        }
        private val placeContext=run {
            val place=ReducedPreparation.places.joinToString("|") { literalPattern(it) }
            Regex("^(?i:no|na|num|numa)\\s+(?i:$place)(?:\\s+(?i:do|da|de)\\s+\\p{L}+(?:\\s+\\p{L}+)?|(?:\\s+\\p{Lu}\\p{L}*){1,2})?$")
        }
        private val structural=setOf("paguei","pagamos","pagou","gastei","gastou","comprei","comprou","recebi","recebemos","debitei","foi","era","foram","deu","cobrou","ficou","custou","saiu","sairam","anote","anota","anotar","anotei","coloque","usei","entrou","entraram","vendi","vendemos","ganhei","pagaram","passei","vou","receber","hoje","ontem","anteontem","dia","parcelas","parcela","vezes","deixei")
    }
}
