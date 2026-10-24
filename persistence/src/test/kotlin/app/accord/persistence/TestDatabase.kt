package app.accord.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** Banco real em memória, como nos testes JVM da fachada (S1 "Decisões de teste"). */
class TestDatabase private constructor(val db: AccordDatabase) {
    fun close() = db.close()

    companion object {
        fun create() = TestDatabase(
            Room.inMemoryDatabaseBuilder<AccordDatabase>(factory = { AccordDatabase_Impl() })
                .setDriver(BundledSQLiteDriver())
                .build(),
        )
    }
}
