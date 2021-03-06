package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by jon on 13/03/18.
 * Contains category info for results
 */

@Entity (
        foreignKeys = @ForeignKey(entity = WjrEvent.class, parentColumns = "wjrId", childColumns = "wjrEventId"),
        indices = {@Index({"wjrEventId"})})
public class WjrCategory {
    @PrimaryKey
    /* Use WJR category IDs for WJR events, INT_MAX and descending for local events */
    public int wjrCategoryId;

    public int wjrEventId;

    String categoryName;

    public WjrCategory(int wjrCategoryId, int wjrEventId, String categoryName) {
        this.wjrCategoryId = wjrCategoryId;
        this.wjrEventId = wjrEventId;
        this.categoryName = categoryName;
    }

    public String toString() { return categoryName; }
}
