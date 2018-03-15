package ca.orienteeringbc.nfctiming;


import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */
public class StartFragment extends Fragment {
    private SharedPreferences sharedPrefs;
    private WjrDatabase database;

    // The event id
    private int eventId;

    public StartFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_start, container, false);

        // Initialize shared prefs
        sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Initialize database
        // FIXME - Move queries off of main thread
        database = Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        eventId = sharedPrefs.getInt(MainActivity.SELECTED_EVENT_KEY, -1);

        if (eventId > 0) {
            List<Competitor> competitors = database.daoAccess().getCompetitorsByEvent(eventId);
            List<WjrCategory> categories = database.daoAccess().getCategoryById(eventId);
            ListView  startList = view.findViewById(R.id.startlist_listview);
            ArrayAdapter<Competitor> adapter = new StartListArrayAdapter(getActivity(), competitors, categories);
            startList.setAdapter(adapter);
        } else {
            // TODO - Warn about no event selected
        }

        return view;
    }
}
