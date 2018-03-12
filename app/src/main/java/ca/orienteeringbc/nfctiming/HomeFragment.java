package ca.orienteeringbc.nfctiming;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;


/**
 * A simple {@link Fragment} subclass.
 */
public class HomeFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private NetworkFragment mNetworkFragment;

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private boolean mDownloading = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Set listeners
        Button getClubs = view.findViewById(R.id.get_clubs);
        Button getEvents = view.findViewById(R.id.get_events);
        getClubs.setOnClickListener(this);
        getEvents.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.get_clubs:
                if (!mDownloading && mNetworkFragment == null) {
                    mNetworkFragment = NetworkFragment.getInstance(
                            getActivity().getSupportFragmentManager(),
                            "https://whyjustrun.ca/iof/3.0/organization_list.xml");
                    // Execute the async download.
                    mNetworkFragment.startDownload();
                    mDownloading = true;
                }
                break;
            case R.id.get_events:
                // TODO - Fix event link
                if (!mDownloading && mNetworkFragment == null) {
                    mNetworkFragment = NetworkFragment.getInstance(
                            getActivity().getSupportFragmentManager(),
                            "https://whyjustrun.ca/iof/3.0/events/500/entry_list.xml");
                    // Execute the async download.
                    mNetworkFragment.startDownload();
                    mDownloading = true;
                }
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (view.getId())
        {
            case R.id.club_spinner:
                // TODO - Download events
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

    public void finishDownloading() {
        mDownloading = false;
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
            mNetworkFragment = null;
        }
    }

    public void addClubs() {
        File clubsFile = new File(getActivity().getFilesDir().toString() + "/clubs.xml");
        if (clubsFile.exists()) {
            // Parse the clubs file as XML doc and add entries to club spinner
        }
    }

}
