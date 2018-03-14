package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

/**
 * Created by jon on 14/03/18.
 * Database abstraction
 */

@Database(entities = {Competitor.class, WjrCategory.class, WjrEvent.class}, version = 1, exportSchema = false)
public abstract class WjrDatabase extends RoomDatabase {
    public abstract DaoAccess daoAccess();
}
