package ca.orienteeringbc.nfctiming;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements DownloadCallback {

    private TextView mNetworkStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
        bottomNavigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.bottombaritem_home:
                                HomeFragment frag = new HomeFragment();
                                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                                transaction.replace(R.id.frame_fragmentholder, frag);
                                transaction.addToBackStack(null);

                                transaction.commit();

                                return true;
                            case R.id.bottombaritem_start:
                                // TODO
                                return true;
                            case R.id.bottombaritem_config:
                                // TODO
                                return true;
                        }
                        return false;
                    }
                });

        mNetworkStatus = findViewById(R.id.network_progress);

    }

    @Override
    public void finishDownloading() {
        HomeFragment frag = (HomeFragment) getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
        if (frag != null)
            frag.finishDownloading();
    }

    // Returns string downloaded
    // TODO - Modify NetworkFragment to download files and return filename
    @Override
    public void updateFromDownload(String result) {
        if(result != null)
            mNetworkStatus.setText(result);
        else
            mNetworkStatus.setText(R.string.error_connect);
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            case Progress.ERROR:
                mNetworkStatus.setText(R.string.error_connect);
                break;
            case Progress.CONNECT_SUCCESS:
                mNetworkStatus.setText(R.string.connected);
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                mNetworkStatus.setText(R.string.stream_assigned);
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                mNetworkStatus.setText(getResources().getString(R.string.downloading, percentComplete));
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                mNetworkStatus.setText(R.string.success);
                break;
        }
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }
}
