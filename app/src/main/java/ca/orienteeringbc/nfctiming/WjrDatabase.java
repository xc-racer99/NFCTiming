package ca.orienteeringbc.nfctiming;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.migration.Migration;

/**
 * Created by jon on 14/03/18.
 * Database abstraction
 */

@Database(entities = {Competitor.class, WjrCategory.class, WjrEvent.class}, version = 2, exportSchema = false)
public abstract class WjrDatabase extends RoomDatabase {
    public abstract DaoAccess daoAccess();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add a new not-null column, defaulting to 2 (OK)
            database.execSQL("ALTER TABLE Competitor ADD COLUMN status INTEGER NOT NULL DEFAULT 2");
        }
    };
}