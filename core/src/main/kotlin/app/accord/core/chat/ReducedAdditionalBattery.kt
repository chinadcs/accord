package app.accord.core.chat

import app.accord.core.Money
import java.security.MessageDigest
import java.time.LocalDate

/** Authored before v2 implementation/inference; supplementary synthetic data, not an independent blind test. */
object ReducedAdditionalBattery {
    const val VERSION=1
    private val ctx=ReducedBattery.context()
    private fun c(id:Int,group:String,text:String,p:Proposal,e:ReducedBattery.Expected,context:ChatContext=ctx)=
        ReducedBattery.Case("n%02d".format(id),group,text,context,p,e)
    private fun reg(id:Int,text:String,description:String,value:String,cents:Long,pay:ChatPayment?,nature:Nature=Nature.Expense,
        account:String?=null,date:DateExpression?=null,day:LocalDate=ReducedBattery.today,installments:String?=null,count:Int=1,
        role:AmountRole=AmountRole.Single,category:String?=null)=c(id,"register",text,
        Proposal.Register(nature,description,value,role.takeIf { count>1 },installments,date,category,pay,account),
        ReducedBattery.Expected.Register(CompleteEntry(nature,description,Money(cents),role,count,day,category?.let { ctx.category(it)!!.id },pay,
            if(nature==Nature.Income || pay in listOf(ChatPayment.Pix,ChatPayment.Debit)) account?.let { ctx.account(it)!!.id } ?: "account-1" else null,null)))
    private fun correct(id:Int,text:String,p:Proposal.Correct,change:EntryChange)=c(id,"correct",text,p,ReducedBattery.Expected.Correct(change))
    private fun answer(id:Int,text:String,field:QuestionField,p:Proposal.Answer,value:AnswerValue):ReducedBattery.Case {
        val options=when(field) {
            QuestionField.Payment->EntryCompleter.paymentOptions(ctx)
            QuestionField.Account->EntryCompleter.accountOptions(ctx)
            QuestionField.AmountRole->EntryCompleter.roleOptions()
            QuestionField.Date->EntryCompleter.dateOptions(ctx.today)
            else->emptyList()
        }
        return c(id,"answer",text,p,ReducedBattery.Expected.Answer(value),ctx.copy(question=OpenQuestion("q1","question-1",field,options)))
    }
    val cases=listOf(
        reg(1,"no cartão foram 47 de estacionamento","estacionamento","47",4700,ChatPayment.Card),
        reg(2,"a compra do casaco deu 360 em 4x no cartão","casaco","360",36000,ChatPayment.Card,installments="4x",count=4,role=AmountRole.Total),
        reg(3,"entraram 270 do bico","bico","270",27000,null,Nature.Income),
        reg(4,"conserto da porta 95 no débito pelo Itaú","conserto da porta","95",9500,ChatPayment.Debit,account="a2"),
        reg(5,"gastei 24 com sementes em espécie","sementes","24",2400,ChatPayment.Cash),
        reg(6,"era para anotar o pão de 11 no pix","pão","11",1100,ChatPayment.Pix),
        reg(7,"vou receber 180 de comissão mês que vem","comissão","180",18000,null,Nature.Income,date=DateExpression(DateKind.NextMonth,"mês que vem"),day=LocalDate.of(2026,11,26)),
        reg(8,"limpeza da casa foi 140 no pix","limpeza da casa","140",14000,ChatPayment.Pix),
        reg(9,"paguei o salário do jardineiro 190 no pix","jardineiro","190",19000,ChatPayment.Pix),
        reg(10,"gasto 59 no cartão, categoria Lazer","gasto","59",5900,ChatPayment.Card,category="c9"),
        c(11,"register","comprei pilhas por 17",Proposal.Register(description="pilhas",amount="17"),ReducedBattery.Expected.Ask(QuestionField.Payment)),
        c(12,"register","anota o almoço no débito",Proposal.Register(description="almoço",payment=ChatPayment.Debit),ReducedBattery.Expected.Ask(QuestionField.Amount)),
        correct(13,"o certo era 52,80",Proposal.Correct("r1",CorrectionField.Amount,text="52,80"),EntryChange.Amount(Money(5280))),
        correct(14,"quis dizer no débito da conta Nubank",Proposal.Correct("r1",CorrectionField.Payment,payment=ChatPayment.Debit,account="a1"),EntryChange.Payment(ChatPayment.Debit,"account-1")),
        correct(15,"ajuste a categoria para Lazer",Proposal.Correct("r1",CorrectionField.Category,category="c9"),EntryChange.Category("lazer")),
        correct(16,"a descrição correta é conserto do portão",Proposal.Correct("r1",CorrectionField.Description,text="conserto do portão"),EntryChange.Description("conserto do portão")),
        correct(17,"coloca a data de anteontem",Proposal.Correct("r1",CorrectionField.Date,date=DateExpression(DateKind.DayBeforeYesterday,"anteontem")),EntryChange.Date(LocalDate.of(2026,10,24))),
        correct(18,"o pagamento foi em espécie",Proposal.Correct("r1",CorrectionField.Payment,payment=ChatPayment.Cash),EntryChange.Payment(ChatPayment.Cash,null)),
        answer(19,"R$ 18,70",QuestionField.Amount,Proposal.Answer("q1",text="18,70"),AnswerValue.Amount(Money(1870))),
        answer(20,"era 200",QuestionField.Amount,Proposal.Answer("q1",text="200"),AnswerValue.Amount(Money(20000))),
        answer(21,"anteontem",QuestionField.Date,Proposal.Answer("q1",date=DateExpression(DateKind.DayBeforeYesterday,"anteontem")),AnswerValue.Day(LocalDate.of(2026,10,24))),
        answer(22,"pelo cartão",QuestionField.Payment,Proposal.Answer("q1",choice="card"),AnswerValue.Option(OptionValue.Card)),
        answer(23,"foi a conta Itaú",QuestionField.Account,Proposal.Answer("q1",choice="a2"),AnswerValue.Option(OptionValue.Account("account-2"))),
        answer(24,"o total da compra",QuestionField.AmountRole,Proposal.Answer("q1",choice="total"),AnswerValue.Option(OptionValue.Role(AmountRole.Total))),
        c(25,"other","me diz quanto já foi gasto",Proposal.Query,ReducedBattery.Expected.Reply(StandardReply.Query)),
        c(26,"other","talvez eu compre um fone por 230",Proposal.Intent,ReducedBattery.Expected.Reply(StandardReply.Intent)),
        c(27,"other","paguei 130 da fatura",Proposal.Unsupported(UnsupportedType.InvoicePayment),ReducedBattery.Expected.Reply(StandardReply.UnsupportedInvoicePayment)),
        c(28,"other","estornaram os 28 da compra",Proposal.Unsupported(UnsupportedType.Refund),ReducedBattery.Expected.Reply(StandardReply.UnsupportedRefund)),
        c(29,"other","café 12 e suco 9 no pix",Proposal.Uninterpretable,ReducedBattery.Expected.Safe),
        c(30,"other","coloque 45 no pix da conta Safra",Proposal.Uninterpretable,ReducedBattery.Expected.Safe),
    )
    val sha256:String by lazy {
        MessageDigest.getInstance("SHA-256").digest(cases.joinToString("\n") { "${it.id}|${it.text}|${it.context}|${ProposalCodec.encode(it.oracle)}|${it.expected}" }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
