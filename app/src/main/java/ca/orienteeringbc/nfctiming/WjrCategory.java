package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by jon on 13/03/18.
 * Contains category info for results
 */

@Entity (foreignKeys = @ForeignKey(entity = WjrEvent.class, parentColumns = "wjrId", childColumns = "wjrEventId"))
public class WjrCategory {
    @PrimaryKey
    public int wjrCategoryId;

    public int wjrEventId;

    public String categoryName;

    // In metres
    public int length;

    public WjrCategory(int wjrCategoryId, int wjrEventId, String categoryName, int length) {
        this.wjrCategoryId = wjrCategoryId;
        this.wjrEventId = wjrEventId;
        this.categoryName = categoryName;
        this.length = length;
    }
}
