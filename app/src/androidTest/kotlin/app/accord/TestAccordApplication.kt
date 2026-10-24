package app.accord

/** Aplicativo dos testes de instrumentação: começa com a conta e o espaço de teste já abertos. */
class TestAccordApplication : AccordApplication() {
    override fun createContainer(): AppContainer = DevIdentity.container(this)
}
