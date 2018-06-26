package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class WjrPerson {
    @PrimaryKey
    public int wjrId;

    public String firstName;
    public String lastName;

    WjrPerson(int wjrId, String firstName, String lastName) {
        this.wjrId = wjrId;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
