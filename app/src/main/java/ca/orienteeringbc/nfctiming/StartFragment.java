package ca.orienteeringbc.nfctiming;


import android.app.AlertDialog;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

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
            final List<Competitor> competitors = database.daoAccess().getCompetitorsByEvent(eventId);
            final List<WjrCategory> categories = database.daoAccess().getCategoryById(eventId);
            ListView  startList = view.findViewById(R.id.startlist_listview);
            final ArrayAdapter<Competitor> adapter = new StartListArrayAdapter(getActivity(), competitors, categories);
            startList.setAdapter(adapter);

            // Setup add new person
            Button button = view.findViewById(R.id.add_new_person);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getActivity());
                    View mView = layoutInflaterAndroid.inflate(R.layout.add_new_competitor_dialog, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                    alertDialogBuilder.setView(mView);

                    final EditText firstName = mView.findViewById(R.id.first_name_input);
                    final EditText lastName = mView.findViewById(R.id.last_name_input);
                    final Spinner spinner = mView.findViewById(R.id.new_person_category_spinner);
                    ArrayAdapter<WjrCategory> catAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, categories);
                    spinner.setAdapter(catAdapter);
                    alertDialogBuilder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Competitor competitor = new Competitor(eventId, firstName.getText().toString(), lastName.getText().toString());
                            WjrCategory category = (WjrCategory) spinner.getSelectedItem();
                            competitor.wjrCategoryId = category.wjrCategoryId;
                            database.daoAccess().insertCompetitors(competitor);
                            adapter.add(competitor);
                        }
                    }).setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialogBox, int id) {
                                    dialogBox.cancel();
                                }
                    }).show();
                }
            });
        } else {
            // TODO - Warn about no event selected
        }

        return view;
    }
}
