package ca.orienteeringbc.nfctiming;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * A simple {@link Fragment} subclass.
 */
public class HomeFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    // A list of all clubs in WJR database
    private Spinner clubSpinner;
    private List<Entry> mClubList = new ArrayList<>();
    private ArrayAdapter clubAdapter;

    // All events for the club
    private Spinner eventSpinner;
    private List<Entry> mEventList = new ArrayList<>();
    private ArrayAdapter eventAdapter;

    // The WJR id of the chosen club and event
    private int clubId = -1;
    private int eventId = -1;

    // SharedPrefs and key
    SharedPreferences sharedPref;
    public static final String CLUBS_LIST_KEY = "CLUBS_LIST_KEY";
    public static final String EVENTS_LIST_KEY = "EVENTS_LIST_KEY";
    public static final String SELECTED_CLUB_KEY = "SELECTED_WJR_CLUB";
    public static final String SELECTED_EVENT_KEY = "SELECTED_WJR_EVENT";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize sharedPrefs
        sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Set button listener
        Button getClubs = view.findViewById(R.id.get_clubs);
        Button getEvents = view.findViewById(R.id.get_events);
        getClubs.setOnClickListener(this);
        getEvents.setOnClickListener(this);

        // Setup club spinner and entries
        mClubList = setToEntries(sharedPref.getStringSet(CLUBS_LIST_KEY, null));
        clubSpinner = view.findViewById(R.id.club_spinner);
        clubSpinner.setOnItemSelectedListener(this);
        clubAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mClubList);
        clubSpinner.setAdapter(clubAdapter);
        int selectedClub = findPositionById(mClubList, sharedPref.getInt(SELECTED_CLUB_KEY, -1));
        if (selectedClub >= 0 && selectedClub < mClubList.size())
            clubSpinner.setSelection(selectedClub);

        // Setup event spinner
        mEventList = setToEntries((sharedPref.getStringSet(EVENTS_LIST_KEY, null)));
        eventSpinner = view.findViewById(R.id.event_spinner);
        eventSpinner.setOnItemSelectedListener(this);
        eventAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mEventList);
        eventSpinner.setAdapter(eventAdapter);
        int selectedEvent = findPositionById(mClubList, sharedPref.getInt(SELECTED_EVENT_KEY, -1));
        if (selectedEvent >= 0 && selectedEvent < mEventList.size())
            clubSpinner.setSelection(selectedEvent);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.get_clubs:
                new DownloadClubTask().execute("https://whyjustrun.ca/iof/3.0/organization_list.xml");
                break;
            case R.id.get_events:
                Log.e("ClubId", "" + clubId);
                if (clubId > 0) {
                    Calendar temp = Calendar.getInstance();
                    long now = temp.getTimeInMillis() / 1000;
                    // 604800 is one week in seconds
                    long start = now - 604800;
                    long end = now + 604800;
                    String url = "https://whyjustrun.ca/events.xml?iof_version=3.0&start=" + start + "&end=" + end + "&club_id=" + clubId;
                    new DownloadEventTask().execute(url);
                }
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        SharedPreferences.Editor editor = sharedPref.edit();

        switch (parent.getId())
        {
            case R.id.club_spinner:
                clubId = mClubList.get(position).getId();
                editor.putInt(SELECTED_CLUB_KEY, clubId);
                break;
            case R.id.event_spinner:
                eventId = mEventList.get(position).getId();
                editor.putInt(SELECTED_EVENT_KEY, eventId);
                break;
        }

        editor.apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing?
    }

    // Converts List<Entry> to Set<String>
    private Set<String> entriesToSet(List<Entry> entries) {
        Set<String> strings = new HashSet<>();
        for (Entry entry : entries)
            strings.add(entry.toString() + "," + entry.getId());

        return strings;
    }

    // Converts Set<String> to List<Entry>
    private List<Entry> setToEntries(Set<String> strings) {
        List<Entry> entries = new ArrayList<>();
        if (strings == null)
            return entries;
        for (String string : strings) {
            String[] parts = string.split(",");
            if (parts.length != 2)
                continue;
            entries.add(new Entry(parts[0], Integer.parseInt(parts[1])));
        }
        return entries;
    }

    // Returns position in ArrayList by id
    private int findPositionById(List<Entry> entries, int id) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).getId() == id)
                return i;
        return -1;
    }

    // Implementation of AsyncTask used to download XML from WJR for Clubs
    private class DownloadClubTask extends AsyncTask<String, Void, List<Entry>> {

        @Override
        protected List<Entry> doInBackground(String... urls) {
            try {
                return loadClubXmlFromNetwork(urls[0]);
            } catch (IOException e) {
                return null;
            } catch (XmlPullParserException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Entry> entries) {
            if (entries != null) {
                clubAdapter.clear();
                clubAdapter.addAll(entries);
                clubAdapter.notifyDataSetChanged();
                clubSpinner.setSelection(0);

                // Also clear event list
                eventAdapter.clear();
                eventAdapter.notifyDataSetChanged();
                eventSpinner.setSelection(0);

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putStringSet(CLUBS_LIST_KEY, entriesToSet(entries));
                editor.putStringSet(EVENTS_LIST_KEY, null);
                editor.putInt(SELECTED_CLUB_KEY, -1);
                editor.putInt(SELECTED_EVENT_KEY, -1);
                editor.apply();
            }
        }
    }

    // Implementation of AsyncTask used to download XML from WJR for Clubs
    private class DownloadEventTask extends AsyncTask<String, Void, List<Entry>> {

        @Override
        protected List<Entry> doInBackground(String... urls) {
            try {
                return loadEventXmlFromNetwork(urls[0]);
            } catch (IOException e) {
                return null;
            } catch (XmlPullParserException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Entry> entries) {
            if (entries != null) {
                eventAdapter.clear();
                eventAdapter.addAll(entries);
                eventAdapter.notifyDataSetChanged();
                eventSpinner.setSelection(0);

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putStringSet(EVENTS_LIST_KEY, entriesToSet(entries));
                editor.putInt(SELECTED_EVENT_KEY, -1);
                editor.apply();
            }
        }
    }

    // Downloads club xml and parses
    private List<Entry> loadClubXmlFromNetwork(String urlString) throws XmlPullParserException, IOException {
        InputStream stream = null;
        WjrClubParser xmlParser = new WjrClubParser();
        List<Entry> entries;

        try {
            stream = downloadUrl(urlString);
            entries = xmlParser.parse(stream);
            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        return entries;
    }

    // Downloads club events xml and parses
    private List<Entry> loadEventXmlFromNetwork(String urlString) throws XmlPullParserException, IOException {
        InputStream stream = null;
        WjrEventParser xmlParser = new WjrEventParser();
        List<Entry> entries;

        try {
            stream = downloadUrl(urlString);
            entries = xmlParser.parse(stream);
            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        return entries;
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an input stream.
    private InputStream downloadUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000 /* milliseconds */);
        conn.setConnectTimeout(15000 /* milliseconds */);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        // Starts the query
        conn.connect();
        return conn.getInputStream();
    }


    // This class represents a single club or event
    public static class Entry {
        private final String title;
        private final int id;

        Entry(String title, int id) {
            this.title = title;
            this.id = id;
        }

        public int getId() { return id; }
        public String toString() { return title; }
    }
}
