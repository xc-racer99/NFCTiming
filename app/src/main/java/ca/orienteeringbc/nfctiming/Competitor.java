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
    public static String[] statuses = {"DNS", "DNF", "OK", "MP", "NC"};
    public static String[] longStatuses = {"DidNotStart", "DidNotFinish", "OK", "MissingPunch", "NotCompeting"};

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

    // DNS
    public int status = 0;

    public Competitor(int wjrEventId, String firstName, String lastName) {
        this.wjrEventId = wjrEventId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Competitor(final Competitor competitor) {
        this.internalId = competitor.internalId;
        this.wjrEventId = competitor.wjrEventId;
        this.wjrId = competitor.wjrId;
        this.nfcTagId = competitor.nfcTagId;
        this.firstName = competitor.firstName;
        this.lastName = competitor.lastName;
        this.wjrCategoryId = competitor.wjrCategoryId;
        this.startTime = competitor.startTime;
        this.endTime = competitor.endTime;
        this.status = competitor.status;
    }

    public String toString() {
        return firstName + " " + lastName;
    }

    public static int statusToInt(String status) {
        switch (status) {
            case "DNS":
                return 0;
            case "DNF":
                return 1;
            case "OK":
                return 2;
            case "MP":
                return 3;
            case "NC":
                return 4;
        }
        return -1;
    }
}
