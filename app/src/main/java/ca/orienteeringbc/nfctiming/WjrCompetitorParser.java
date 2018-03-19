/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Created by jon on 12/03/18.
 * Designed to read https://whyjustrun.ca/iof/3.0/organization_list.xml
 * and return the club (short) name and WJR id
 */

package ca.orienteeringbc.nfctiming;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

public class WjrCompetitorParser {
    private static final String ns = null;

    // We don't use namespaces

    private HomeFragment.EventInfo eventInfo;
    private int eventId = -1;
    private String eventName;

    public HomeFragment.EventInfo parse(InputStream in) throws XmlPullParserException, IOException {
        eventInfo = new HomeFragment.EventInfo();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            return readFeed(parser);
        } finally {
            in.close();
        }
    }

    private HomeFragment.EventInfo readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "EntryList");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            switch (name) {
                case "Event":
                    readEvent(parser);
                    break;
                case "PersonEntry":
                    eventInfo.competitors.add(readCompetitor(parser));
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        return eventInfo;
    }

    // Parses the contents of an organisation. If it encounters a ShortName or Id tag, hands them
    // off to their respective methods for processing. Otherwise, skips the tag.
    private void readEvent(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "Event");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            switch (name) {
                case "Id":
                    eventId = readId(parser);
                    break;
                case "Name":
                    eventName = readName(parser);
                    break;
                case "Class":
                    eventInfo.categories.add(readCategory(parser));
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        eventInfo.event = new WjrEvent(eventId, eventName);
    }

    // Processes title tags in the feed.
    private int readId(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "Id");
        int id = Integer.parseInt(readText(parser));
        parser.require(XmlPullParser.END_TAG, ns, "Id");
        return id;
    }

    // Processes summary tags in the feed.
    private String readName(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "Name");
        String name = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "Name");
        return name;
    }

    // Processes Class tags in the feed
    private WjrCategory readCategory(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "Class");
        int catId = -1;
        String name = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Id":
                    catId = readId(parser);
                    break;
                case "Name":
                    name = readName(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Class");

        return new WjrCategory(catId, eventId, name);
    }

    private Competitor readCompetitor(XmlPullParser parser) throws IOException, XmlPullParserException {
        Competitor competitor = null;
        int classId = -1;
        parser.require(XmlPullParser.START_TAG, ns, "PersonEntry");
        // Id under PersonEntry is registration Id, need Id from Person
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Person":
                    competitor = readPerson(parser);
                    break;
                case "Class":
                    classId = readClassId(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "PersonEntry");
        if (competitor != null) {
            competitor.wjrCategoryId = classId;
        }
        return competitor;
    }

    private Competitor readPerson(XmlPullParser parser) throws IOException, XmlPullParserException {
        Competitor competitor;
        String firstName = null;
        String lastName = null;
        int compId = -1;
        parser.require(XmlPullParser.START_TAG, ns, "Person");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Id":
                    compId = readId(parser);
                    break;
                case "Name":
                    String[] names = readNameParts(parser);
                    if (names.length == 2) {
                        firstName = names[0];
                        lastName = names[1];
                    }
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Person");
        competitor = new Competitor(eventId, firstName, lastName);
        competitor.wjrId = compId;
        return competitor;
    }

    private int readClassId(XmlPullParser parser) throws IOException, XmlPullParserException {
        int classId = -1;
        parser.require(XmlPullParser.START_TAG, ns, "Class");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Id":
                    classId = readId(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Class");
        return classId;
    }

    private String[] readNameParts(XmlPullParser parser) throws IOException, XmlPullParserException {
        String[] names = new String[2];
        parser.require(XmlPullParser.START_TAG, ns, "Name");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Given":
                    names[0] = readText(parser);
                    break;
                case "Family":
                    names[1] = readText(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Name");
        return names;
    }

    // Extracts the text value of a text
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    // Skips tags the parser isn't interested in. Uses depth to handle nested tags. i.e.,
    // if the next tag after a START_TAG isn't a matching END_TAG, it keeps going until it
    // finds the matching END_TAG (as indicated by the value of "depth" being 0).
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}