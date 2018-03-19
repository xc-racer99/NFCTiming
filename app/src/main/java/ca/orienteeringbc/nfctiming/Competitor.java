package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by jon on 13/03/18.
 * Contains info for each competitor in an event
 */

@Entity (
        foreignKeys = @ForeignKey(entity = WjrEvent.class, parentColumns = "wjrId", childColumns = "wjrEventId"),
        indices = {@Index(value = { "firstName", "lastName", "wjrEventId", "wjrId" }, unique = true)})
public class Competitor {
    @PrimaryKey(autoGenerate = true)
    int internalId;

    // The event id
    public int wjrEventId;

    public int wjrId = -1;

    public long nfcTagId;

    public String firstName;

    public String lastName;

    public int wjrCategoryId = -1;

    // In seconds
    public long startTime = 0;
    public long endTime = 0;

    public Competitor(int wjrEventId, String firstName, String lastName) {
        this.wjrEventId = wjrEventId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String toString() {
        return lastName + ", " + firstName;
    }
}
