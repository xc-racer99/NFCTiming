package ca.orienteeringbc.nfctiming;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LocalHomeFragment extends Fragment {
    private WjrDatabase database;

    // The event id
    private int eventId;

    private View view;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        eventId = getArguments().getInt(MainActivity.SELECTED_EVENT_KEY);

        view = inflater.inflate(R.layout.fragment_local_home, container, false);

        return view;
    }
}
