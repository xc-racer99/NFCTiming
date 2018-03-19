package ca.orienteeringbc.nfctiming;

import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by jon on 16/03/18.
 * Reads values from database and uploads xml
 */

public class UploadResultsXml {
    // No namespace needed
    private static final String ns = null;

    public boolean makeXml(OutputStream outputStream, WjrDatabase database, int eventId) throws IOException {
        XmlSerializer serializer = Xml.newSerializer();

        List<WjrCategory> categories = database.daoAccess().getCategoryById(eventId);

        try {
            serializer.setOutput(outputStream, "UTF-8");
            serializer.startTag(ns, "ResultList");
            serializer.attribute(ns,"iofVersion", "3.0");

            // Event info start
            serializer.startTag(ns, "Event");

            // Event ID
            serializer.startTag(ns, "Id");
            serializer.attribute(ns, "type", "WhyJustRun");
            serializer.text(String.valueOf(eventId));
            serializer.endTag(ns, "Id");

            // Categories
            for (WjrCategory category : categories) {
                writeClass(serializer, category);
            }
            // End Event info
            serializer.endTag(ns, "Event");

            // Start class results
            for (WjrCategory category : categories) {
                List<Competitor> competitors = database.daoAccess().getResultsByCategory(eventId, category.wjrCategoryId);

                // ClassResult start
                serializer.startTag(ns, "ClassResult");

                // Write class info
                writeClass(serializer, category);

                // Write competitor info
                int pos = 1;
                for (Competitor competitor : competitors) {
                    if (writeCompetitor(serializer, competitor, pos))
                        pos++;
                }

                // End class result
                serializer.endTag(ns, "ClassResult");
            }

            serializer.endTag(ns, "ResultList");
            serializer.flush();
        } finally {
            outputStream.close();
        }
        return true;
    }

    private void writeClass(XmlSerializer serializer, WjrCategory category) throws IOException {
        serializer.startTag(ns, "Class");
        writeWjrId(serializer, category.wjrCategoryId);
        serializer.startTag(ns, "Name");
        serializer.text(category.categoryName);
        serializer.endTag(ns, "Name");
        serializer.endTag(ns, "Class");
    }

    // Writes a competitor with given position, returns true if status OK
    private boolean writeCompetitor(XmlSerializer serializer, Competitor competitor, int pos) throws IOException {
        boolean ret = false;

        serializer.startTag(ns, "PersonResult");

        // Person start
        serializer.startTag(ns, "Person");
        int wjrId = competitor.wjrId;
        if (wjrId != -1)
            writeWjrId(serializer, wjrId);

        // Name start
        serializer.startTag(ns, "Name");
        serializer.startTag(ns, "Given");
        serializer.text(competitor.firstName);
        serializer.endTag(ns, "Given");
        serializer.startTag(ns, "Family");
        serializer.text(competitor.lastName);
        serializer.endTag(ns, "Family");
        serializer.endTag(ns, "Name");
        // Name end

        serializer.endTag(ns, "Person");
        // Person end

        // Result start
        serializer.startTag(ns, "Result");
        if (competitor.startTime != 0 && competitor.endTime != 0) {
            // Competitor finished, return increased position
            ret = true;

            // Time
            serializer.startTag(ns, "Time");
            serializer.text(String.valueOf(competitor.endTime - competitor.startTime));
            serializer.endTag(ns, "Time");

            // Position
            serializer.startTag(ns, "Position");
            serializer.attribute(ns, "type", "Course");
            serializer.text(String.valueOf(pos));
            serializer.endTag(ns, "Position");

            // Status
            writeStatus(serializer, "OK");
        } else if (competitor.startTime > 0){
            // Competitor didn't finish
            writeStatus(serializer, "DidNotFinish");
        } else {
            // Competitor didn't start
            writeStatus(serializer, "DidNotStart");
        }
        serializer.endTag(ns, "Result");

        serializer.endTag(ns, "PersonResult");

        return ret;
    }

    private void writeWjrId(XmlSerializer serializer, int id) throws IOException {
        serializer.startTag(ns, "Id");
        serializer.attribute(ns, "type", "WhyJustRun");
        serializer.text(String.valueOf(id));
        serializer.endTag(ns, "Id");
    }

    private void writeStatus(XmlSerializer serializer, String status) throws IOException {
        serializer.startTag(ns, "Status");
        serializer.text(status);
        serializer.endTag(ns, "Status");
    }
}
