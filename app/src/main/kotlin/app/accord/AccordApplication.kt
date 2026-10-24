package app.accord

import android.app.Application

open class AccordApplication : Application() {
    /** Ligação manual por construtores; injeção de dependência segue como escolha aberta. */
    lateinit var container: AppContainer

    /** Os testes de instrumentação trocam o contêiner (conta e serviço de teste) sem mexer no código de produção. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }
}
