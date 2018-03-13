package ca.orienteeringbc.nfctiming;

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
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */
public class HomeFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    // A list of all clubs in WJR database
    private List<Entry> mClubList = new ArrayList<>();
    private ArrayAdapter clubAdapter;

    // All events for the club
    private List<Entry> mEventList = new ArrayList<>();
    private ArrayAdapter eventAdapter;

    // The WJR id of the chosen club and event
    private int clubId = -1;
    private int eventId = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Set button listener
        Button getClubs = view.findViewById(R.id.get_clubs);
        Button getEvents = view.findViewById(R.id.get_events);
        getClubs.setOnClickListener(this);
        getEvents.setOnClickListener(this);

        // Setup club spinner
        Spinner clubSpinner = view.findViewById(R.id.club_spinner);
        clubSpinner.setOnItemSelectedListener(this);
        clubAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mClubList);
        clubSpinner.setAdapter(clubAdapter);

        // Setup event spinner
        Spinner eventSpinner = view.findViewById(R.id.event_spinner);
        eventSpinner.setOnItemSelectedListener(this);
        eventAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mEventList);
        eventSpinner.setAdapter(eventAdapter);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.get_clubs:
                new DownloadClubTask().execute("https://whyjustrun.ca/iof/3.0/organization_list.xml");
                break;
            case R.id.get_events:
                Log.e("ClubId:", "" + clubId);
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
        switch (parent.getId())
        {
            case R.id.club_spinner:
                clubId = mClubList.get(position).getId();
                break;
            case R.id.event_spinner:
                // TODO
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing?
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

                // Also clear event list
                eventAdapter.clear();
                eventAdapter.notifyDataSetChanged();
            }

            // TODO - Save club/events?
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
            }
            // TODO - Save club/events?
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
