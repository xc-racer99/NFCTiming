package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by jon on 13/03/18.
 * Contains basic event info for results
 */

@Entity
public class WjrEvent {
    @PrimaryKey
    public int wjrId;

    public int wjrClubId;

    public String eventName;

    public WjrEvent(int wjrId, int wjrClubId, String eventName) {
        this.wjrId = wjrId;
        this.wjrClubId = wjrClubId;
        this.eventName = eventName;
    }

    public String toString() {
        return eventName;
    }
}
