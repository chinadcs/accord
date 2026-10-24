package app.accord

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class AccordTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestAccordApplication::class.java.name, context)
}
