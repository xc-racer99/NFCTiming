package ca.orienteeringbc.nfctiming;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

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
            new SetupResultList().execute();
        }

        return view;
    }

    private class SetupResultList extends AsyncTask<Void, Void, List<Competitor>> {
        @Override
        protected List<Competitor> doInBackground(Void... voids) {
            return database.daoAccess().getCompetitorsByEvent(eventId);
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
}
