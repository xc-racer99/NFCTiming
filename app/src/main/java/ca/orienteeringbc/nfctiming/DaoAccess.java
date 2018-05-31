package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

/**
 * Created by jon on 13/03/18.
 * Data Access Object
 */

@Dao
public interface DaoAccess {

    // Competitor insert methods
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void insertCompetitors(Competitor... competitors);

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void insertCompetitorList(List<Competitor> competitors);

    // Competitor update methods
    @Update (onConflict = OnConflictStrategy.REPLACE)
    int updateCompetitor(Competitor... competitors);

    @Update (onConflict = OnConflictStrategy.REPLACE)
    int updateCompetitorList(List<Competitor> competitors);

    // Competitor access methods
    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND nfcTagId = :nfcTagId")
    Competitor getCompetitorByNfc(int wjrEventId, long nfcTagId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND wjrId = :wjrId")
    Competitor getCompetitorByWjrId(int wjrEventId, int wjrId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId ORDER BY LOWER(lastName)")
    List<Competitor> getCompetitorsByEventAlphabetically(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId ORDER BY endTime - startTime")
    List<Competitor> getCompetitorsByEventTimed(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND startTime = 0 ORDER BY LOWER(lastName) ASC")
    List<Competitor> getUnstartedCompetitorsByEvent(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND wjrCategoryId = :wjrCatId ORDER BY endTime - startTime")
    List<Competitor> getResultsByCategory(int wjrEventId, int wjrCatId);

    // WjrCategory setter
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addCategories(List<WjrCategory> categoryList);

    // WjrCategory queries
    @Query("SELECT * FROM WjrCategory WHERE wjrEventId = :wjrEventId")
    List<WjrCategory> getCategoryById(int wjrEventId);

    // WjrEvent setter
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addEvent(WjrEvent event);

    // WjrEvent queries
    @Query("SELECT eventName FROM WjrEvent WHERE wjrId = :wjrEventId")
    String getEventNameById(int wjrEventId);
}
