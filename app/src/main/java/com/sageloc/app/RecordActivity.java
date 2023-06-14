package com.sageloc.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;


public class RecordActivity extends Activity {

    static String scenarioType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
    }

    public void onRadioButtonClicked(View view) {

        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.FP:
                if (checked)
                    scenarioType = "FP";
                break;
            case R.id.TP:
                if (checked)
                    scenarioType = "TP";
                break;
            case R.id.FN:
                if (checked)
                    scenarioType = "FN";
                break;
            case R.id.TN:
                if (checked)
                    scenarioType = "TN";
                break;
        }

    }

    public void sendRecord(View view)
    {
         AliensAppActivity.printRecord(scenarioType);
         final String currentMessage2 = "Scenario recorded\n";
         runOnUiThread(new Runnable() {
             public void run() {
                 Toast.makeText(RecordActivity.this, currentMessage2, Toast.LENGTH_SHORT).show();
             }
         });

        finish();
    }

    public void noAction(View view){
            finish();
    }

}