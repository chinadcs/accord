package app.accord

import android.app.Application

class AccordApplication : Application() {
    /** Ligação manual por construtores; injeção de dependência segue como escolha aberta. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
