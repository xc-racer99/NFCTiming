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

    // Competitor access methods
    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND nfcTagId = :nfcTagId")
    Competitor getCompetitorByNfc(int wjrEventId, long nfcTagId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND wjrId = :wjrId")
    List<Competitor> getCompetitorsByWjrId(int wjrEventId, int wjrId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId ORDER BY LOWER(firstName)")
    List<Competitor> getCompetitorsByEventAlphabetically(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId ORDER BY endTime - startTime")
    List<Competitor> getCompetitorsByEventTimed(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId ORDER BY wjrCategoryId, endTime - startTime")
    List<Competitor> getCompetitorsByEventCategory(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND nfcTagId <= 0 ORDER BY LOWER(firstName)")
    List<Competitor> getUnstartedCompetitorsByEvent(int wjrEventId);

    @Query("SELECT * FROM Competitor WHERE wjrEventId = :wjrEventId AND wjrCategoryId = :wjrCatId ORDER BY endTime - startTime")
    List<Competitor> getResultsByCategory(int wjrEventId, int wjrCatId);

    // Competitor cleanup
    @Query("DELETE FROM Competitor WHERE wjrEventId IN (:oldEvents)")
    void cleanupOldCompetitors(int[] oldEvents);

    // WjrCategory setters
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addCategories(List<WjrCategory> categoryList);

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addCategory(WjrCategory category);

    // WjrCategory queries
    @Query("SELECT * FROM WjrCategory WHERE wjrEventId = :wjrEventId")
    List<WjrCategory> getCategoryById(int wjrEventId);

   @Query("DELETE FROM WjrCategory WHERE wjrEventId IN (:oldEvents)")
   void cleanupOldCategories(int[] oldEvents);

    // WjrEvent setters
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addEventsList(List<WjrEvent> events);

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addEvent(WjrEvent event);

    // WjrEvent queries
    @Query("SELECT eventName FROM WjrEvent WHERE wjrId = :wjrEventId")
    String getEventNameById(int wjrEventId);

    @Query("SELECT * FROM WjrEvent WHERE wjrClubId = :wjrClubId")
    List<WjrEvent> getEventsByClub(int wjrClubId);

    @Query("SELECT wjrId FROM WjrEvent WHERE wjrClubId = :wjrClubId AND NOT wjrId IN (:currentEvents)")
    int[] getEventsToRemove(int wjrClubId, List<Integer> currentEvents);

    @Query("DELETE FROM WjrEvent WHERE wjrClubId = :wjrClubId AND NOT wjrId IN (:currentEvents)")
    void deleteOldEvents(int wjrClubId, List<Integer> currentEvents);

    // WjrClub setter
    @Insert (onConflict =  OnConflictStrategy.REPLACE)
    void addClubsList(List<WjrClub> clubs);

    // WjrClub queries
    @Query("DELETE FROM WjrClub")
    void deleteAllClubs();

    @Query("SELECT * FROM WjrClub ORDER BY wjrId ASC")
    List<WjrClub> getAllClubs();

    // WjrPerson setter
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void addPeopleList(List<WjrPerson> people);

    // WjrPerson queries
    @Query("DELETE FROM WjrPerson")
    void deleteAllPeople();

    @Query("SELECT * FROM WjrPerson WHERE LOWER(lastName) = LOWER(:lastName) AND LOWER(firstName) = LOWER(:firstName)")
    WjrPerson getPersonByName(String firstName, String lastName);

    @Query("SELECT * FROM WjrPerson WHERE wjrId = :wjrId")
    WjrPerson getPersonById(int wjrId);
}
