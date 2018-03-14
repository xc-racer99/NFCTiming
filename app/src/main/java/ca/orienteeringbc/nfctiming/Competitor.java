package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by jon on 13/03/18.
 * Contains info for each competitor in an event
 */

@Entity (foreignKeys = @ForeignKey(entity = WjrEvent.class, parentColumns = "wjrId", childColumns = "wjrEventId"))
public class Competitor {
    @PrimaryKey(autoGenerate = true)
    public int internalId;

    // The event id
    public int wjrEventId;

    public int wjrId;

    public int nfcTagId;

    public String firstName;

    public String lastName;

    // In seconds
    public long startTime;
    public long endTime;

    public Competitor(int wjrEventId, String firstName, String lastName) {
        this.wjrEventId = wjrEventId;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
