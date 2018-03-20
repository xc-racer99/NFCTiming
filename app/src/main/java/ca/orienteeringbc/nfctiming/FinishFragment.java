package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */
public class FinishFragment extends Fragment {
    private WjrDatabase database;

    // The event id
    private int eventId;

    // View created
    private View view;

    public FinishFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_finish, container, false);

        // Initialize shared prefs
        SharedPreferences sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Initialize database
        database = Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();

        // Determine event ID
        eventId = sharedPrefs.getInt(MainActivity.SELECTED_EVENT_KEY, -1);

        if (eventId > 0) {
            new SetupEventName().execute();
            setupResultList();

            Button uploadResults = view.findViewById(R.id.upload_results);
            uploadResults.setEnabled(true);
            uploadResults.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new UploadResultsTask().execute();
                }
            });
        }

        return view;
    }

    protected void setupResultList() {
        new SetupResultListTask().execute();
    }

    private class SetupResultListTask extends AsyncTask<Void, Void, List<Competitor>> {
        @Override
        protected List<Competitor> doInBackground(Void... voids) {
            return database.daoAccess().getCompetitorsByEventTimed(eventId);
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            ListView listView = view.findViewById(R.id.results_listview);
            FinishArrayAdapter adapter = new FinishArrayAdapter(getActivity(), competitors);
            listView.setAdapter(adapter);
        }
    }

    // Sets the event name field
    private class SetupEventName extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            return database.daoAccess().getEventNameById(eventId);
        }

        @Override
        protected void onPostExecute(String name) {
            TextView eventName = view.findViewById(R.id.event_title);
            eventName.setText(name);
        }
    }

    // Uploads a results xml
    private class UploadResultsTask extends AsyncTask<Void, Void, Boolean> {

        // TODO - Currently this saves the file to the SDCard
        @Override
        protected Boolean doInBackground(Void... voids) {
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "test.xml"));
                new UploadResultsXml().makeXml(fos, database, eventId);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success)
                Toast.makeText(getActivity(), R.string.success_uploading, Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(getActivity(), R.string.error_uploading, Toast.LENGTH_LONG).show();
        }
    }
}
