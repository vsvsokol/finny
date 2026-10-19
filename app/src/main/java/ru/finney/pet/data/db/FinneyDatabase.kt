package ru.finney.pet.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Схема каждой версии экспортируется в app/schemas и коммитится.
 *
 * С версии 3 изменения переносятся миграциями — [MIGRATION_3_4] … [MIGRATION_11_12]. Базы версий 1–2
 * пересоздаются с нуля: версия 3 переименовала питомцев, а перенести старые значения было бы
 * возможно только храня прежние имена прямо в коде.
 */
@Database(
    entities = [
        ProfileEntity::class,
        PetStateEntity::class,
        PeriodEntity::class,
        LedgerEntryEntity::class,
        TaskAttemptEntity::class,
        ItemUseEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class FinneyDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {
        const val NAME = "finney.db"

        fun create(context: Context): FinneyDatabase =
            Room.databaseBuilder(context.applicationContext, FinneyDatabase::class.java, NAME)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        /**
         * 3 → 4: надетый аксессуар. Колонка допускает NULL — у всех существующих
         * профилей ничего не надето, остальные данные не трогаются.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN wornItemId TEXT")
            }
        }

        /**
         * Сон у питомцев, созданных до версии 5. Столько же, сколько у нового питомца после
         * первого спада (90 − 60): потребность уже видна, но питомец ещё не «устал».
         */
        const val ENERGY_ON_MIGRATION = "30"

        /** 4 → 5: шкала сна и время, когда питомец лёг спать (никто не спит). Остальное не трогается. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet_state ADD COLUMN energy INTEGER NOT NULL DEFAULT $ENERGY_ON_MIGRATION")
                db.execSQL("ALTER TABLE pet_state ADD COLUMN sleepingSince INTEGER")
            }
        }

        /**
         * 5 → 6: игра уровня. У старых периодов её нет: закрытые считаются пройденными
         * по игре (NULL читается как true), в текущем игра не обязательна.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE periods ADD COLUMN levelTaskId TEXT")
                db.execSQL("ALTER TABLE periods ADD COLUMN gamePassed INTEGER")
            }
        }

        /**
         * 6 → 7: сессия игры с игрушками. Сама игрушка — покупка, она уже в ledger;
         * в pet_state добавляется только счётчик сессии. У всех — сессии нет.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pet_state ADD COLUMN playMood INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pet_state ADD COLUMN playSince INTEGER")
            }
        }

        /**
         * 7 → 8: итог уровня записывается при закрытии — демо-режим облегчает переход,
         * и выключенный демо-режим не должен пересчитывать прошлое. У закрытых раньше
         * периодов NULL: они решаются по обычным правилам, как и решались.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE periods ADD COLUMN passed INTEGER")
            }
        }

        /**
         * 8 → 9: у попытки хранится зерно чисел игры — по нему попытку можно повторить
         * и проверить. У старых попыток NULL: их числа были без разброса.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_attempts ADD COLUMN seed INTEGER")
            }
        }

        /** 9 → 10: попытка помнит, засчитан ли бонус, — бонусная награда выдаётся один раз. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_attempts ADD COLUMN bonus INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 10 → 11: очки — второй слот аксессуара, носятся вместе со шляпой. У старых профилей NULL. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN wornEyesId TEXT")
            }
        }

        /**
         * 11 → 12: полка расходников — что взято с неё, отдельно от покупки. До версии 12 всё
         * купленное применялось сразу, поэтому каждой старой покупке пишется её использование
         * в тот же момент: полка у старых профилей пустая. Для шляп и игрушек такая запись
         * ничего не значит — полку считают только по расходникам.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `item_uses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, `periodNumber` INTEGER NOT NULL, `itemId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_uses_profileId` ON `item_uses` (`profileId`)")
                db.execSQL(
                    "INSERT INTO item_uses (profileId, periodNumber, itemId, createdAt) " +
                        "SELECT profileId, periodNumber, itemId, createdAt FROM ledger " +
                        "WHERE type = 'PURCHASE' AND itemId IS NOT NULL ORDER BY id",
                )
            }
        }
    }
}
