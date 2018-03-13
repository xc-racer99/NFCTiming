package ca.orienteeringbc.nfctiming;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {

    public static final String SELECTED_CLUB_KEY = "SELECTED_WJR_CLUB";
    public static final String SELECTED_EVENT_KEY = "SELECTED_WJR_EVENT";
    public static final String WJR_USERNAME = "WJR_USERNAME";
    public static final String WJR_PASSWORD = "WJR_PASSWORD";

    int currentFrame = 0;

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
                                if (currentFrame != 0) {
                                    addHomeFragment();
                                    currentFrame = 0;
                                }

                                return true;
                            case R.id.bottombaritem_start:
                                if (currentFrame != 1) {
                                    addStartFragment();
                                    currentFrame = 1;
                                }
                                return true;
                            case R.id.bottombaritem_config:
                                if (currentFrame != 2) {
                                    addFinishFragment();
                                    currentFrame = 2;
                                }
                                return true;
                        }
                        return false;
                    }
                });

        // Initialize to home view
        addHomeFragment();
    }

    private void addHomeFragment() {
        HomeFragment frag = new HomeFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commit();
    }

    private void addStartFragment() {
        StartFragment frag = new StartFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commit();
    }

    private void addFinishFragment() {
        // TODO
    }
}
