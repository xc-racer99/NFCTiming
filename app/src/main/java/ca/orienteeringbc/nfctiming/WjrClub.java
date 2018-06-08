package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/*
 * Holds all of the clubs that have been downloaded
 * Only need the (short) name  and the Wjr club ID
 */

@Entity
public class WjrClub {
    @PrimaryKey
    public int wjrId;

    public String clubName;

    public WjrClub(int wjrId, String clubName) {
        this.wjrId = wjrId;
        this.clubName = clubName;
    }

    public String toString() {
        return clubName;
    }
}
