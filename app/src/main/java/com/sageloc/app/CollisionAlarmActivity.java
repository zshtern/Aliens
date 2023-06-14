package com.sageloc.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

/**
 * This activity plays sound and vibration alarm and shows winking danger sign on the screen
 */
public class CollisionAlarmActivity extends Activity {

    long[] vibrationPattern = {
        100, 100, 100, 100, 100, 100, 300, 100, 300, 100, 300, 100, 100, 100, 100, 100, 100, 100
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        //playSound();
        setContentView(R.layout.activity_collision_alarm);
        vibrateAlarm();
        performAnimation(R.anim.grow);

        // ends this activity and returns to parent
        try {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        finish();
                    } catch (Exception e) {
                        AliensAppLogger.Log("Exception " + e.toString());
                    }
                }
            }, 3000);
        } catch (Throwable t){
            AliensAppLogger.Log("Exception " + t.toString());
        }
    }

    private void performAnimation(int animationResourceID) {
        // We will animate the imageview
        ImageView reusableImageView = (ImageView) findViewById(R.id.ImageViewForTweening);
        //	reusableImageView.setImageResource(R.drawable.green_rect);
        reusableImageView.setImageResource(R.drawable.moving_vehicle);
        reusableImageView.setVisibility(View.VISIBLE);

        // Load the appropriate animation
        Animation an = AnimationUtils.loadAnimation(this, animationResourceID);
        // Register a listener, so we can disable and re-enable buttons
     //   an.setAnimationListener(new MyAnimationListener());
        // Start the animation
        reusableImageView.startAnimation(an);
    }

    /*private void playSound() {

        final MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.annoying_alarm_clock);
        mediaPlayer.start();

        try {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    finish();
                }
            }, 3000);
        } catch (Throwable t){
            Log.i("playSound", "Thread  exception " + t);
        }
    }*/

    protected void onDestroy() {
        super.onDestroy();
    }


    public void vibrateAlarm() {
        Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
   //           if (vibe.hasVibrator()==true) {   //***call require API level 11
        vibe.vibrate(vibrationPattern, -1);
  /*         } else {
                Context context = getApplicationContext();
                CharSequence text = "Your device does not have a vibrator!";
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }   */
    }


   @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Navigate "up" the demo structure to the launchpad activity.
                // See http://developer.android.com/design/patterns/navigation.html for more.
                NavUtils.navigateUpTo(this, new Intent(this, AliensAppActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
