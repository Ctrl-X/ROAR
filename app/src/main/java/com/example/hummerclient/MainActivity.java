package com.example.hummerclient;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.lifecycle.ViewModelProvider;

import com.example.hummerclient.databinding.ActivityMainBinding;
import com.example.hummerclient.networking.IpChecker;
import com.example.hummerclient.game.RemoteControllerFragment;
import com.example.hummerclient.game.RoverFragment;
import com.example.hummerclient.game.GameModel;
import com.example.hummerclient.ui.StartMenuFragment;
import com.example.hummerclient.game.XboxPad;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {
    private static String TAG = "ROAR";
    XboxPad xboxPad = null;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler(Looper.myLooper());

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mFullScreenContainer.getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mFullScreenContainer.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private final Runnable mShowPart2Runnable = () -> {
        // Delayed display of UI elements
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = () -> hide();

    private ActivityMainBinding binding;
    private GameModel gameModel;
    private IpChecker ipChecker = null;
    private Runnable updateIpAddress = null;
    private FragmentContainerView mFullScreenContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mFullScreenContainer = binding.fullScreenContainer;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Set up the user interaction to manually show or hide the system UI.
        mFullScreenContainer.setOnClickListener(view -> toggle());

        updateIpAddress = () -> {
            String myIpAddr = ipChecker.getIpAddress();
            gameModel.setMyAddr(myIpAddr);
            gameModel.setStatus("Mon address ip est " + myIpAddr);
            Log.i(TAG, "My ip address is now " + ipChecker.getIpAddress());
        };

        gameModel = new ViewModelProvider(this).get(GameModel.class);

        // When it's running, hide the start menu and launch the activity
        gameModel.getIsRunning().observe(this, isRunning -> {
            if (isRunning) {
                Boolean isRemoteController = gameModel.getIsRemoteController().getValue();
                if (isRemoteController) {
                    startRemotecontroller();
                } else {
                    startRover();
                }
            }

        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        MenuItem item = menu.getItem(0);
        item.setOnMenuItemClickListener(item1 -> {
            showStartMenu();
            return true;
        });
        return true;
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    public void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 30) {
            mFullScreenContainer.getWindowInsetsController().show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            mFullScreenContainer.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }


    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }


    protected void onResume() {
        super.onResume();

        gameModel.setStatus("Recherche de l'adresse IP...");
        ipChecker = new IpChecker(() -> runOnUiThread(updateIpAddress));
        ipChecker.start();

    }


    protected void onPause() {
        super.onPause();

        if (ipChecker != null && ipChecker.isAlive()) {
            ipChecker.interrupt();
        }
    }

    private void startRemotecontroller() {

        RemoteControllerFragment fragment = new RemoteControllerFragment();
        xboxPad = new XboxPad(fragment);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fullScreenContainer, fragment)
                .commit();
    }

    private void startRover() {
        RoverFragment fragment = new RoverFragment();
        xboxPad = null;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fullScreenContainer, fragment)
                .commit();
    }

    private void showStartMenu() {
        gameModel.setIsRunning(false);
        Fragment fragment = new StartMenuFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fullScreenContainer, fragment)
                .commit();
        // TODO : Faire le code RemoteController avec envoi reseau
    }


    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (xboxPad != null) {
            boolean result = xboxPad.onGenericMotionEvent(event);
            if (result) {
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (xboxPad != null) {
            boolean result = xboxPad.onKeyDown(keyCode, event);
            if (result) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (xboxPad != null) {
            boolean result = xboxPad.onKeyUp(keyCode, event);
            if (result) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
