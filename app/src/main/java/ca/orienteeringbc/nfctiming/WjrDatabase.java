package ca.orienteeringbc.nfctiming;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.migration.Migration;

/**
 * Created by jon on 14/03/18.
 * Database abstraction
 */

@Database(entities = {Competitor.class, WjrCategory.class, WjrEvent.class, WjrClub.class, WjrPerson.class }, version = 4)
public abstract class WjrDatabase extends RoomDatabase {
    public abstract DaoAccess daoAccess();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add a new not-null column, defaulting to 2 (OK)
            database.execSQL("ALTER TABLE Competitor ADD COLUMN status INTEGER NOT NULL DEFAULT 2");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE WjrEvent ADD COLUMN wjrClubId INTEGER NOT NULL DEFAULT -1");
            database.execSQL("CREATE TABLE IF NOT EXISTS WjrClub (wjrId INTEGER NOT NULL, clubName TEXT, PRIMARY KEY(wjrId))");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Drop index and recreate
            database.execSQL("DROP INDEX index_Competitor_firstName_lastName_wjrEventId_wjrId");
            database.execSQL("CREATE  INDEX `index_Competitor_firstName_lastName_wjrEventId_wjrId` ON `Competitor` (`firstName`, `lastName`, `wjrEventId`, `wjrId`)");

            // Create new index on Competitor
            database.execSQL("CREATE  INDEX `index_Competitor_wjrEventId` ON `Competitor` (`wjrEventId`)");

            // Create new index on WjrCategory
            database.execSQL("CREATE  INDEX `index_WjrCategory_wjrEventId` ON `WjrCategory` (`wjrEventId`)");

            // Create new table
            database.execSQL("CREATE TABLE IF NOT EXISTS `WjrPerson` (`wjrId` INTEGER NOT NULL, `firstName` TEXT, `lastName` TEXT, PRIMARY KEY(`wjrId`))");
        }
    };
}