package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Room;
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
import android.widget.EditText;
import android.widget.Spinner;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static ca.orienteeringbc.nfctiming.MainActivity.SELECTED_CLUB_KEY;
import static ca.orienteeringbc.nfctiming.MainActivity.SELECTED_EVENT_KEY;
import static ca.orienteeringbc.nfctiming.MainActivity.WJR_PASSWORD;
import static ca.orienteeringbc.nfctiming.MainActivity.WJR_USERNAME;


/**
 * A simple {@link Fragment} subclass.
 */
public class HomeFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    // Password and username field
    private EditText wjrUsername;
    private EditText wjrPassword;

    // A list of all clubs in WJR database
    private Spinner clubSpinner;
    private List<Entry> mClubList = new ArrayList<>();
    private ArrayAdapter clubAdapter;

    // All events for the club
    private Spinner eventSpinner;
    private List<Entry> mEventList = new ArrayList<>();
    private ArrayAdapter eventAdapter;

    // Buttons
    private Button getCompetitors;
    private Button getEvents;

    // The WJR id of the chosen club and event
    private int clubId = -1;
    private int eventId = -1;

    // SharedPrefs
    private SharedPreferences sharedPref;

    // Database
    private WjrDatabase database;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize sharedPrefs
        sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Setup EditTexts
        wjrPassword = view.findViewById(R.id.wjr_pass);
        wjrUsername = view.findViewById(R.id.wjr_user);

        String savedUser = sharedPref.getString(WJR_USERNAME, "");
        String savedPass = sharedPref.getString(WJR_PASSWORD, "");
        wjrUsername.setText(savedUser);
        wjrPassword.setText(savedPass);

        // Set button listener
        Button savePrefs = view.findViewById(R.id.save_credentials);
        Button getClubs = view.findViewById(R.id.get_clubs);
        getEvents = view.findViewById(R.id.get_events);
        getCompetitors = view.findViewById(R.id.get_competitors);

        savePrefs.setOnClickListener(this);
        getClubs.setOnClickListener(this);
        getEvents.setOnClickListener(this);
        getCompetitors.setOnClickListener(this);

        // Setup club spinner and entries
        try {
            List<Entry> tempClubList = loadClubXmlFromDisk();
            if (tempClubList != null)
                mClubList = tempClubList;
        } catch (IOException e) {
            Log.e("ClubXML", "Failed to create club list - IOException");
        } catch (XmlPullParserException e) {
            Log.e("ClubXML", "Failed to create club list - XmlPullParserException");
        }
        clubSpinner = view.findViewById(R.id.club_spinner);
        clubSpinner.setOnItemSelectedListener(this);
        clubAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mClubList);
        clubSpinner.setAdapter(clubAdapter);
        int selectedClub = findPositionById(mClubList, sharedPref.getInt(SELECTED_CLUB_KEY, -1));
        if (selectedClub >= 0 && selectedClub < mClubList.size()) {
            clubId = sharedPref.getInt(SELECTED_CLUB_KEY, -1);
            clubSpinner.setSelection(selectedClub);
            getEvents.setEnabled(true);
        }

        // Setup event spinner and entries
        try {
            List<Entry> tempEventList = loadEventXmlFromDisk();
            if (tempEventList != null)
                mEventList = tempEventList;
        } catch (IOException e) {
            Log.e("EventXML", "Failed to create event list - IOException");
        } catch (XmlPullParserException e) {
            Log.e("EventXML", "Failed to create event list - XmlPullParserException");
        }
        eventSpinner = view.findViewById(R.id.event_spinner);
        eventSpinner.setOnItemSelectedListener(this);
        eventAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mEventList);
        eventSpinner.setAdapter(eventAdapter);
        int selectedEvent = findPositionById(mEventList, sharedPref.getInt(SELECTED_EVENT_KEY, -1));
        if (selectedEvent >= 0 && selectedEvent < mEventList.size()) {
            eventId = sharedPref.getInt(SELECTED_EVENT_KEY, -1);
            eventSpinner.setSelection(selectedEvent);
            getCompetitors.setEnabled(true);
        }

        // Initialize database
        database = Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.get_clubs:
                new DownloadClubTask().execute("https://whyjustrun.ca/iof/3.0/organization_list.xml");
                break;
            case R.id.get_events:
                Log.d("ClubId", "" + clubId);
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
            case R.id.get_competitors:
                // TODO - Download event xml and save in DB
                break;
            case R.id.save_credentials:
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(WJR_USERNAME, wjrUsername.getText().toString());
                editor.putString(WJR_PASSWORD, wjrPassword.getText().toString());
                editor.apply();
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
                getEvents.setEnabled(true);
                break;
            case R.id.event_spinner:
                eventId = mEventList.get(position).getId();
                editor.putInt(SELECTED_EVENT_KEY, eventId);
                getCompetitors.setEnabled(true);
                break;
        }
        editor.apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        SharedPreferences.Editor editor = sharedPref.edit();
        switch (parent.getId())
        {
            case R.id.club_spinner:
                clubId = -1;
                editor.putInt(SELECTED_CLUB_KEY, -1);
                getEvents.setEnabled(false);
                break;
            case R.id.event_spinner:
                eventId = -1;
                editor.putInt(SELECTED_EVENT_KEY, -1);
                getCompetitors.setEnabled(false);
                break;
        }
        editor.apply();
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

                getEvents.setEnabled(true);

                int pos = findPositionById(mClubList, clubId);
                if (pos >= 0) {
                    clubSpinner.setSelection(pos);
                } else {
                    clubSpinner.setSelection(0);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putInt(SELECTED_CLUB_KEY, -1);
                    editor.apply();

                    // Also clear event list
                    eventAdapter.clear();
                    eventAdapter.notifyDataSetChanged();
                    updateEventId();
                }
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
                updateEventId();
            }
        }
    }

    private void updateEventId() {
        int pos = findPositionById(mEventList, eventId);
        if (pos >= 0) {
            eventSpinner.setSelection(pos);
            getCompetitors.setEnabled(true);
        } else {
            eventSpinner.setSelection(0);
            getCompetitors.setEnabled(false);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(SELECTED_EVENT_KEY, -1);
            editor.apply();
        }
    }

    // Downloads club xml and parses
    private List<Entry> loadClubXmlFromNetwork(String urlString) throws XmlPullParserException, IOException {
        InputStream stream = null;
        FileOutputStream fos = null;

        try {
            stream = downloadUrl(urlString);
            fos = getActivity().openFileOutput("clubs.xml", Context.MODE_PRIVATE);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = stream.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
        return loadClubXmlFromDisk();
    }

    private List<Entry> loadClubXmlFromDisk() throws XmlPullParserException, IOException{
        FileInputStream fis = null;
        WjrClubParser xmlParser = new WjrClubParser();
        List<Entry> entries;

        try {
            fis = new FileInputStream(getActivity().getFileStreamPath("clubs.xml"));
            entries = xmlParser.parse(fis);
            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (fis != null) {
                fis.close();
            }
        }

        return entries;
    }

    // Downloads club xml and parses
    private List<Entry> loadEventXmlFromNetwork(String urlString) throws XmlPullParserException, IOException {
        InputStream stream = null;
        FileOutputStream fos = null;

        try {
            stream = downloadUrl(urlString);
            fos = getActivity().openFileOutput("events_" + clubId + ".xml", Context.MODE_PRIVATE);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = stream.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
        return loadEventXmlFromDisk();
    }

    private List<Entry> loadEventXmlFromDisk() throws XmlPullParserException, IOException{
        FileInputStream fis = null;
        WjrEventParser xmlParser = new WjrEventParser();
        List<Entry> entries;

        try {
            fis = new FileInputStream(getActivity().getFileStreamPath("events_" + clubId + ".xml"));
            entries = xmlParser.parse(fis);
            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (fis != null) {
                fis.close();
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
