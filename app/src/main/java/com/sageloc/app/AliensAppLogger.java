package com.sageloc.app;


import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.sageloc.app.AliensApplication.SENSOR_TESTING_MODE;

public class AliensAppLogger
{
    static String state = Environment.getExternalStorageState();
    static File mLogFile = null;
    static File mEventsFile = null;
    static File mLogDirectory = null;

    public static void Init()
    {
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mLogDirectory = new File(Environment.getExternalStorageDirectory() + File.separator + "AliensApplication" + File.separator + "Logs");
            if (!mLogDirectory.exists()) {
                Log.d("Dir created ", "Dir created ");
                mLogDirectory.mkdirs();
            }

            mLogFile = new File(mLogDirectory, "AliensApp" + System.currentTimeMillis() + ".txt");
            if (!mLogFile.exists()) {
                try {
                    mLogFile.createNewFile();
                    Log.d("File created ", "File created ");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    synchronized public static void Log(String message) {

        Log.i("AliensAppActivity", message);

        if (mLogFile != null)
        {
            try {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(mLogFile, true));
                String logLine = System.currentTimeMillis() + ":" + message;
                buf.write(logLine);
                buf.newLine();
                buf.flush();
                buf.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    synchronized public static void LogEvent(String message) {
        if (!SENSOR_TESTING_MODE || mLogDirectory == null)
            return;

        if (mEventsFile == null)
        {
            mEventsFile = new File(mLogDirectory, "Events" + System.currentTimeMillis() + ".txt");
            if (!mEventsFile.exists()) {
                try {
                    mEventsFile.createNewFile();
                    Log.d("File created ", "File created ");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(mEventsFile, true));
            //String logLine = System.currentTimeMillis() + " " + message; - no need to add timestamp as it is already included in the event itself
            buf.write(message);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
