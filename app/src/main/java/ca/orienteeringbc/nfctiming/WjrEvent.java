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

    public String eventDate;

    public String eventName;

    public WjrEvent(int wjrId, String eventDate, String eventName) {
        this.wjrId = wjrId;
        this.eventDate = eventDate;
        this.eventName = eventName;
    }
}
