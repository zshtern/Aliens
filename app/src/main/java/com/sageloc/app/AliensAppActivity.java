package com.sageloc.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.gavaghan.geodesy.Geodesy;
import org.gavaghan.geodesy.GlobalCoordinates;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.sageloc.app.AliensApplication.SENSOR_TESTING_MODE;

/* ACCELERATION-SENSOR -> import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager; <- ACCELERATION-SENSOR */

//import android.location.LocationManager;
//import android.location.LocationProvider;
//import com.sageloc.kalmanfilter.KalmanLocationManager;


public class AliensAppActivity extends Activity implements
		GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener,
		com.google.android.gms.location.LocationListener,
		ResultCallback<Status>/*,
		SensorEventListener */{

	// Constants
    //Following is the IP address of the chat server. You can change this IP address according to your configuration.
    // I have localhost IP address for Android emulator.
    //private String CHAT_SERVER_IP = "192.168.3.46"; // evgen-pc
    private String CHAT_SERVER_IP = "212.150.196.49"; // pobeda
    //private String CHAT_SERVER_IP = "10.0.0.23"; // local host - evgenyr
    //private String CHAT_SERVER_IP = "10.100.102.8"; // LEVRUBIN network
	//private String CHAT_SERVER_IP = "10.100.102.4"; // Lev network IPv4 address
	//private String CHAT_SERVER_IP = "fe80::b0e4::f14d::47db::68f8%21"; // Lev network IPv6 address

    Tracker mAnalyticsTracker;


	// Request location updates with the highest possible frequency on gps.
    // Typically, this means one update per second for gps.
	//private static final long GPS_TIME = 1000;

	// For the network provider, which gives locations with less accuracy (less reliable),
    //request updates every 5 seconds.
	//private static final long NET_TIME = 5000;

	// For the filter-time argument we use a "real" value: the predictions are triggered by a timer.
    // Lets say we want 5 updates (estimates) per second = update each 200 millis.
	private static final long FILTER_TIME = 200;

    //private static final long NANO_SEC_IN_SEC = 1000000000;

    // this controls how frequent client will update server, to have highest frequency, set it to 0 (for collisions etc).
    private static final long MIN_SERVER_UPDATE_TIME = 500; // milli sec

    private static final long MAX_SERVER_NO_UPDATE_TIME = 5000;
	private static final long MAX_INACTIVITY = 60000; // milli sec - period to declare disconnection from server, if no ack message is received

    private static final float MIN_NON_ZERO_SPEED = 1.0f; // m/s
	private static final float LOW_SPEED = 4.0f; // m/s
	private static final float AVERAGE_SPEED = 8.0f; // m/s
    private static final float HIGH_SPEED = 16.0f; // m/s
    private static final float USEIN_BOLT = 12.4f; // m/s
    private static final float BRAKING_BAD = 4.572f; // m/s

	private static final int ZERO_SPEED_GROUP = 0;
	private static final int LOW_SPEED_GROUP = 1;
	private static final int AVERAGE_SPEED_GROUP = 2;
	private static final int HIGH_SPEED_GROUP = 3;
	private static final int RACE_SPEED_GROUP = 4;

    private static final double MAX_DANGER_DISTANCE = 32;
    private static final double MAX_FOE_DISTANCE = 64;

	private static final float MAX_LOCATION_ACC = 0.5f; // m - max distance between two locations to consider them the same

    // 100 is too big, should be less than 15 in production
    private static final float ACCURACY_THRESHOLD = 100f; // m - above this location updates will be ignored

	private static final String ACTIVITY_EXTRA = "ACTIVITY_EXTRA";
	private static final String DETECTED_ACTIVITIES = "DETECTED_ACTIVITIES";
	private static final String BROADCAST_ACTION = "BROADCAST_ACTION";
	private static final String SHARED_PREFERENCES_NAME = "SHARED_PREFERENCES";
	private static final String ACTIVITY_UPDATES_REQUESTED_KEY = "ACTIVITY_UPDATES_REQUESTED";

	/**
	 * The desired time between activity detections. Larger values result in fewer activity
	 * detections while improving battery life. A value of 0 results in activity detections at the
	 * fastest possible rate. Getting frequent updates negatively impact battery life and a real
	 * app may prefer to request less frequent updates.
	 */
	public static final long DETECTION_INTERVAL_IN_MILLISECONDS = 0;

    enum MonitoredActivitiesIndex {
        STILL,
        ON_FOOT,
        WALKING,
        RUNNING,
        ON_BICYCLE,
        IN_VEHICLE,
        TILTING,
        UNKNOWN
    }
	protected static final int[] MONITORED_ACTIVITIES = {
			DetectedActivity.STILL,
			DetectedActivity.ON_FOOT,
			DetectedActivity.WALKING,
			DetectedActivity.RUNNING,
			DetectedActivity.ON_BICYCLE,
			DetectedActivity.IN_VEHICLE,
			DetectedActivity.TILTING,
			DetectedActivity.UNKNOWN
	};

    enum AlienType { UNKNOWN, PEDESTRIAN, VEHICLE }
    AlienType ownType = AlienType.PEDESTRIAN;


	// Context
	//private KalmanLocationManager mKalmanLocationManager;
	private SharedPreferences mPreferences;

	// UI elements
	private MapView mMapView;
	private RadioButton mServerConnectionStatus;
	private boolean isConnected = false;

	// Map elements
	private GoogleMap mGoogleMap;
    private Polyline mRawPath = null;
    private Polyline mFilteredPath = null;
    private Polyline mCorrectedPath = null;
    private double []mOffsets;

    //private Circle mGpsCircle;
	//private Circle mNetCircle;
	//private Circle mServicesCircle;
    //private Circle mKalmanCircle;

    float zoomLevel = 19;

	// GoogleMaps own OnLocationChangedListener (not android's LocationListener) - shows "my location"
	private LocationSource.OnLocationChangedListener mOnLocationChangedListener;

	/* ACCELERATION-SENSOR -> private SensorManager sensorManager = null;
	private float []lastAccelerationValues = { 0, 0, 0 }; <- ACCELERATION-SENSOR */

	// Communication
	private static Socket client;
	private static PrintWriter printwriter;
	private static BufferedReader bufferedReader;

	// use this wrapper if get android.os.NetworkOnMainThreadException
    static class PrintWriterFromUITask extends AsyncTask<String, Void, Void> {

        private Exception exception;

        protected Void doInBackground(String... toWrite)
        {
            try
            {
                if (printwriter != null  &&  toWrite.length > 0) {
                    printwriter.write(toWrite[0] + "\n");
                    printwriter.checkError(); // checkError calls flush() first...
                    String toLog = "Sent to server: " + toWrite[0];
                    AliensAppLogger.Log(toLog);
                }
            }
            catch (Exception e)
            {
                this.exception = e;
            }
            return null;
        }
    }

    // to be used from background threads
    public static void printRecord (String scenarioType ) {

        if (printwriter != null && scenarioType != null) {
            String message = "rec;" + scenarioType + ";";
            printwriter.write(message + "\n");
            printwriter.flush();
            AliensAppLogger.Log("Sent message to server: " + message);
        }

    }


    // location
	private Location lastLocation;
    private boolean cameraInitialized = false;
	//private Location lastGPSLocation;

	class LocationProjection
	{
		LocationProjection() { coordXY = new double[2]; rawXY = new double[2]; }

        double []coordXY;   // 2d coordinates of lat/lng projection - could be a projection of rawXY on alien path, in case of slow objects
        double []rawXY;   // raw signal as received in location update
		double A = 0.0;
		double B = 0.0;
		boolean direction = true; // true - object moves along X in positive direction

		double velocity = 0.0;
		int velocity_group = ZERO_SPEED_GROUP;

		double SUMX = 0.0;
		double SUMY = 0.0;
		double X2 = 0.0;
		double XY = 0.0;
		double SUMX2 = 0.0;
		double SUMXY = 0.0;
		private long timestamp = 0; // msecs
	}
	private List<LocationProjection> lastLocations;
	private static final int NUMBER_LOCATIONS_FOR_BEARING_CALCULATION = 8;

	private static final HashMap<String, Circle> aliens = new HashMap<>();

    private long lastUpdateTime = 0; // last update message to server
	private long lastAckTime = 0; // last ack message from server
    private long lastMessageAt = 0; // used to avoid too frequent messages in host UI

	private String myID;

	// Google Play Services API
	/**
	 * A receiver for DetectedActivity objects broadcast by the
	 * {@code ActivityDetectionIntentService}.
	 */
	protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;
	/**
	 * Provides the entry point to Google Play services.
	 */
	protected GoogleApiClient mGoogleApiClient;

	protected LocationRequest mLocationRequest;

	/**
	 * The DetectedActivities that we track in this sample. We use this for initializing the
	 * {@code DetectedActivitiesAdapter}. We also use this for persisting state in
	 * {@code onSaveInstanceState()} and restoring it in {@code onCreate()}. This ensures that each
	 * activity is displayed with the correct confidence level upon orientation changes.
	 */
	private ArrayList<DetectedActivity> mDetectedActivities;

/*  tts ->
    private final int CHECK_CODE = 0x1;

    public class Speaker implements TextToSpeech.OnInitListener {

        private TextToSpeech tts;

        private boolean ready = false;
        private HashMap<String, String> hash = new HashMap<String, String>();

        public Speaker(Context context){
            tts = new TextToSpeech(context, this);
        }

        public boolean IsReady() { return ready; }

        @Override
        public void onInit(int status) {
            if(status == TextToSpeech.SUCCESS){
                // Change this to match your
                // locale
                tts.setLanguage(Locale.US);
                hash.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
                ready = true;
            }else{
                ready = false;
            }
        }

        public void speak(String text, boolean isUrgent){

            // Speak only if the TTS is ready
            // and the user has allowed speech

            if (!ready)
                return;

            float speechRate = 1.0f; // normal
            if (isUrgent)
                speechRate = 1.5f;

            tts.setSpeechRate(speechRate);

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, hash);
        }

        public void destroy(){
            tts.shutdown();
        }
    }

    private Speaker speaker;
*/
    private float doubleToFloatSafe(double d)
	{
		Double doubleObject = d;
		return doubleObject.floatValue();
	}

	private static HashSet<String> messages;
    synchronized private void LogFromBackgroundThread(String message)
    {
        long currentTime = System.currentTimeMillis();
        AliensAppLogger.Log(message);

        if (null == messages)
            messages = new HashSet<>();

        if (message.isEmpty())
            return;

		message += "\n";
        messages.add(message);

        if (currentTime - lastMessageAt > 10000) { // don't issue more frequently than 10 sec
            lastMessageAt = currentTime;
            StringBuilder sb = new StringBuilder();
            for (String message1 : messages) {
                sb.append(message1);
            }
            messages.clear();
            final String currentMessage = sb.toString();
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(AliensAppActivity.this, currentMessage, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        // Obtain the shared Tracker instance.
        AliensApplication application = (AliensApplication) getApplication();
        mAnalyticsTracker = application.getDefaultTracker();

        try {

			//Toast.makeText(AliensAppActivity.this, "Hello, Alien!", Toast.LENGTH_SHORT).show();

            String packageName = getPackageName();
            AliensAppLogger.Log("Package name: " + packageName);

            // packageName is defined in \app\src\main\AndroidManifest.xml.
            // we can't just change it there in order to create different applications - many places in source should be also changed then accordingly.
            // so instead there is a specific build type "sensors" defined (to access: Build->Edit Build types) In this build type we add suffix to application id.
            // which in reality also adds it to the package name.
            // to compile normal application - build type debug/release should be used.
            if (packageName.equals("com.sageloc.app"))
                SENSOR_TESTING_MODE = false;

            /* another working option
            if (BuildConfig.APPLICATION_ID.equals("com.sageloc.app")) {
                SENSOR_TESTING_MODE = false;
            } */

			setContentView(R.layout.activity_main);

			// location and movement listeners
			//mKalmanLocationManager = new KalmanLocationManager(this);
			mPreferences = getPreferences(Context.MODE_PRIVATE);

			// Init maps
			int result = MapsInitializer.initialize(this);

			if (result != ConnectionResult.SUCCESS) {

				GooglePlayServicesUtil.getErrorDialog(result, this, 0).show();
				return;
			}

			// UI elements
			mMapView = (MapView) findViewById(R.id.mapView);
			mMapView.onCreate(savedInstanceState);

			mServerConnectionStatus = (RadioButton) findViewById(R.id.serverConnectionState);

			// Map settings
			mGoogleMap = mMapView.getMap();
			UiSettings uiSettings = mGoogleMap.getUiSettings();
			uiSettings.setAllGesturesEnabled(true);
			uiSettings.setCompassEnabled(true);
			uiSettings.setZoomControlsEnabled(true);
			uiSettings.setMyLocationButtonEnabled(true);
			uiSettings.setMapToolbarEnabled(true);
			mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
			mGoogleMap.setLocationSource(mLocationSource);
			mGoogleMap.setMyLocationEnabled(true);

            mGoogleMap.setOnMapClickListener(new OnMapClickListener() {
                @Override
                public void onMapClick(LatLng point) {

                    mGoogleMap.addMarker(new MarkerOptions()
                            .position(point)
                            .draggable(true));
                }
            });
            mGoogleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(final Marker marker) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(AliensAppActivity.this);
                    builder.setMessage(" - 'Report' - if the marker is at correct checkpoint location\n - 'Modify' - to update marker position\n - 'Remove' - to delete the marker")
                            .setTitle("Check point location")
                            .setPositiveButton("Report", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked Done button
                                    HandleCheckPoint(marker.getPosition());
                                }
                            })
                            .setNeutralButton("Modify", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked Continue button
                                }
                            })
                            .setNegativeButton("Remove", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked Remove button
                                    marker.remove();
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_map)
                            .show();


                    // Return false to indicate that we have not consumed the event and that we wish
                    // for the default behavior to occur (which is for the camera to move such that the
                    // marker is centered and for the marker's info window to open, if it has one).
                    return false;
                }
            });

			// Map elements
/*			CircleOptions gpsCircleOptions = new CircleOptions()
					.center(new LatLng(0.0, 0.0))
					.radius(1.0)
					.fillColor(getResources().getColor(R.color.activity_main_fill_gps))
					.strokeColor(getResources().getColor(R.color.activity_main_stroke_gps))
					.strokeWidth(1.0f)
					.visible(false);
			mGpsCircle = mGoogleMap.addCircle(gpsCircleOptions);

			CircleOptions netCircleOptions = new CircleOptions()
					.center(new LatLng(0.0, 0.0))
					.radius(1.0)
					.fillColor(getResources().getColor(R.color.activity_main_fill_net))
					.strokeColor(getResources().getColor(R.color.activity_main_stroke_net))
					.strokeWidth(1.0f)
					.visible(false);
			mNetCircle = mGoogleMap.addCircle(netCircleOptions);
*/
/*			CircleOptions servicesCircleOptions = new CircleOptions()
					.center(new LatLng(0.0, 0.0))
					.radius(1.0)
					.fillColor(getResources().getColor(R.color.activity_main_fill_services))
					.strokeColor(getResources().getColor(R.color.activity_main_stroke_services))
					.strokeWidth(1.0f)
					.visible(false);
			mServicesCircle = mGoogleMap.addCircle(servicesCircleOptions);
*/
/*            CircleOptions kalmanCircleOptions = new CircleOptions()
                    .center(new LatLng(0.0, 0.0))
                    .radius(1.0)
                    .fillColor(getResources().getColor(R.color.activity_main_fill_kalman))
                    .strokeColor(getResources().getColor(R.color.activity_main_stroke_kalman))
                    .strokeWidth(1.0f)
                    .visible(false);
            mKalmanCircle = mGoogleMap.addCircle(kalmanCircleOptions);
*/

            // Google Play Services API
			// Get a receiver for broadcasts from ActivityDetectionIntentService.
			mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();

			// Reuse the value of mDetectedActivities from the bundle if possible. This maintains state
			// across device orientation changes. If mDetectedActivities is not stored in the bundle,
			// populate it with DetectedActivity objects whose confidence is set to 0. Doing this
			// ensures that the bar graphs for only only the most recently detected activities are
			// filled in.
			if (savedInstanceState != null && savedInstanceState.containsKey(DETECTED_ACTIVITIES)) {
				mDetectedActivities = (ArrayList<DetectedActivity>) savedInstanceState.getSerializable(DETECTED_ACTIVITIES);
			} else {
				mDetectedActivities = new ArrayList<DetectedActivity>();

				// Set the confidence level of each monitored activity to zero.
				for (int i = 0; i < MONITORED_ACTIVITIES.length; i++) {
					mDetectedActivities.add(new DetectedActivity(MONITORED_ACTIVITIES[i], 0));
				}
			}

			// Kick off the request to build GoogleApiClient.
			buildGoogleApiClient();

			/* ACCELERATION-SENSOR -> sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
			registerSensorManagerListeners();  <- ACCELERATION-SENSOR */

            /* tts ->
            // Check if text to speach engine is available
            Intent checkTTS = new Intent();
            checkTTS.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            startActivityForResult(checkTTS, CHECK_CODE);
            */

            if (!SENSOR_TESTING_MODE) {

                // Connection with server
                Thread chatOperatorThread = new Thread(new ChatOperator());
                chatOperatorThread.start();

                Thread receiverThread = new Thread(new Receiver());
                receiverThread.start();
            }

        } catch (Exception ex) {
			Toast.makeText(AliensAppActivity.this, String.format("Creation failure" + ex.toString()), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
	 * ActivityRecognition API.
	 */
	protected synchronized void buildGoogleApiClient() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(ActivityRecognition.API)
				.addApi(LocationServices.API)
				.build();

        mGoogleApiClient.connect();

		createLocationRequest();
	}

	protected void createLocationRequest() {
		mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(100);
		mLocationRequest.setFastestInterval(100);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	}

	/* ACCELERATION-SENSOR ->
		public void registerSensorManagerListeners() {
            sensorManager.registerListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
     <- ACCELERATION-SENSOR */

	@Override
    protected void onStart() {
		super.onStart();
		//mGoogleApiClient.connect();
	}

	@Override
	protected void onStop() {
		super.onStop();
		//mGoogleApiClient.disconnect();

	/* ACCELERATION-SENSOR ->
		sensorManager.unregisterListener(this);
	<- ACCELERATION-SENSOR */
	}

	/**
	 * Runs when a GoogleApiClient object successfully connects.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		//LogFromBackgroundThread("Connected to GoogleApiClient");

		// Solution for
		// java.lang.SecurityException: Activity detection usage requires the com.google.android.gms.permission.ACTIVITY_RECOGNITION permission
		// (from https://code.google.com/p/android/issues/detail?id=61850)
		getActivityDetectionPendingIntent().cancel();

		/**
		 * Registers for activity recognition updates using
		 * {@link com.google.android.gms.location.ActivityRecognitionApi#requestActivityUpdates} which
		 * returns a {@link com.google.android.gms.common.api.PendingResult}. Since this activity
		 * implements the PendingResult interface, the activity itself receives the callback, and the
		 * code within {@code onResult} executes. Note: once {@code requestActivityUpdates()} completes
		 * successfully, the {@code DetectedActivitiesIntentService} starts receiving callbacks when
		 * activities are detected.
		 */
		ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
				mGoogleApiClient,
				DETECTION_INTERVAL_IN_MILLISECONDS,
				getActivityDetectionPendingIntent()
		).setResultCallback(this);


        lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
		if (null != lastLocation) {
            // Update blue "myLocation" dot
            runOnUiThread(new Runnable() {
							  public void run() {

								  zoomLevel = mGoogleMap.getCameraPosition().zoom;
								  if (zoomLevel < 19)
									  zoomLevel = 19;

								  mOnLocationChangedListener.onLocationChanged(lastLocation);

								  if (!cameraInitialized) {
                                      cameraInitialized = true;

                                      // update camera position
                                      LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                                      CameraPosition position = CameraPosition.builder(mGoogleMap.getCameraPosition())
                                              .target(latLng)
                                              .bearing(lastLocation.getBearing())
                                              .zoom(zoomLevel)
                                              .build();

                                      CameraUpdate update = CameraUpdateFactory.newCameraPosition(position);
                                      mGoogleMap.animateCamera(update, (int) FILTER_TIME, null);
                                  }
							  }
						  }
			);
        }

		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// Refer to the javadoc for ConnectionResult to see what error codes might be returned in
		// onConnectionFailed.
		//LogFromBackgroundThread("Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
	}

	@Override
	public void onConnectionSuspended(int cause) {
		// The connection to Google Play services was lost for some reason. We call connect() to
		// attempt to re-establish the connection.
		//LogFromBackgroundThread("Connection suspended");
		mGoogleApiClient.connect();
	}

	@Override
	public void onLocationChanged(Location location) {

		if (null != location) {
			/*LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
			mServicesCircle.setCenter(latLng);
			mServicesCircle.setRadius(3.0);
			mServicesCircle.setVisible(true);
            */
            HandleLocationUpdate(location);
		}
	}

/* tts ->
   @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode == CHECK_CODE){
            if(resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS){
                speaker = new Speaker(this);
            }else {
                Intent install = new Intent();
                install.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(install);
            }
        }
    }
*/

/* ACCELERATION-SENSOR ->
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				// 2 - safe de/acc, 4 - extreme
				if ((lastAccelerationValues[0] != 0 && Math.abs(lastAccelerationValues[0] - event.values[0]) > 15) ||
					(lastAccelerationValues[1] != 0 && Math.abs(lastAccelerationValues[1] - event.values[1]) > 15) ||
					(lastAccelerationValues[2] != 0 && Math.abs(lastAccelerationValues[2] - event.values[2]) > 15)) {
					String accelerationValues = "Got moderate acceleration: " +
							Math.abs(lastAccelerationValues[0] - event.values[0]) + ", " +
							Math.abs(lastAccelerationValues[1] - event.values[1]) + ", " +
							Math.abs(lastAccelerationValues[2] - event.values[2]);
					LogFromBackgroundThread(accelerationValues);
				}
				System.arraycopy(event.values, 0, lastAccelerationValues, 0, 3);
				break;
		}
	}
 <- ACCELERATION-SENSOR */

    private class AlertHandler implements Runnable {

        int dangerLevel = 1;
        AlienType foeAlienType = AlienType.PEDESTRIAN;

        AlertHandler(int danger, AlienType alienType ) { this.dangerLevel = danger; this.foeAlienType = alienType; }

        public void run() {

            //* playing a mp3 clip:
            //final MediaPlayer mediaPlayer = MediaPlayer.create(AliensAppActivity.this, R.raw.electronic_chime);
            //mediaPlayer.start();
            //try {
            //    Thread.sleep(1000);
            //}
            //catch (InterruptedException ie)
            //{
            //}

            // TODO - handle mute
            // TODO - handle already playing audio (today alarm is playing in parallel)
            final AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
            ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            //final MediaPlayer mediaPlayer = MediaPlayer.create(AliensAppActivity.this, R.raw.electronic_chime);

            MediaPlayer mediaPlayer = MediaPlayer.create(AliensAppActivity.this, R.raw.annoying_alarm_clock);
            if (1 == dangerLevel)
            {
                if (foeAlienType == AlienType.PEDESTRIAN)
                    mediaPlayer = MediaPlayer.create(AliensAppActivity.this, R.raw.pedestrian_down_the_road);
                else if (foeAlienType == AlienType.VEHICLE)
                    mediaPlayer = MediaPlayer.create(AliensAppActivity.this, R.raw.vehicle_approaching);
            }

            mediaPlayer.start();
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException ie)
            {
            }
            //mediaPlayer.stop();

//  tts->
//            int duration = 1000;
//            int iterations = 1;
//            boolean isUrgent = false;
//            switch (dangerLevel)
//            {
//                case 1:
//                    duration = 1000;
//                    iterations = 1;
//                    isUrgent = false;
//                    break;
//
//                case 2:
//                    duration = 500;
//                    iterations = 2;
//                    isUrgent = true;
//                    break;
//            }
//            for (int i = 0; i < iterations; ++i)
//            {
//                if (null != speaker && speaker.IsReady())
//                {
//                    speaker.speak(pedestrian, isUrgent);
//                }
//                else
//                {
//                    toneG.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, duration);
//                    //mediaPlayer.start();
//                }
//                try {
//                    Thread.sleep(duration);
//                }
//                catch (InterruptedException ie)
//                {
//                }
//            }

            // plays currently set alarm ringtone
            //try {
            //    Uri a = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            //    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), a);
            //    r.play();
            //} catch (Exception e) {
            //    e.printStackTrace();
            //}
        }
    }

	private class ChatOperator implements Runnable {

		private String outerMessage;

        // GETS THE IP ADDRESS OF YOUR PHONE'S NETWORK
		private String getLocalIpAddress() {
			try {
				for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
					NetworkInterface intf = en.nextElement();
					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
						InetAddress inetAddress = enumIpAddr.nextElement();
						if (!inetAddress.isLoopbackAddress()) { return inetAddress.getHostAddress(); }
					}
				}
			} catch (SocketException ex) {
				LogFromBackgroundThread(String.format("Failed to get IP address " + ex.toString()));
			}
			return null;
		}

		@Override
		public void run()
        {
			long counter = 0;
			String myIP = getLocalIpAddress();

			TelephonyManager tMgr = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
			myID = tMgr.getDeviceId();

			final String message1 = "My ID: " + myID;
            AliensAppLogger.Log(message1);

            while (true) {
				try {
                    // we do not update server without having accurate location
                    if (null != lastLocation  &&  lastLocation.hasAccuracy()  &&  lastLocation.getAccuracy() < ACCURACY_THRESHOLD)
                    {
                        boolean previousConnectionStatus = isConnected;
                        if (!isConnected) {
                            client = new Socket();
                            // If we change port number here,
                            //we should change this number also in "AliensServer.java "ServerSocket serverSocket = new ServerSocket()"
                            client.connect(new InetSocketAddress(CHAT_SERVER_IP, 444), 1000);
                            client.setSoTimeout(1000); // read timeout

                            outerMessage = "Connected!";

                            printwriter = new PrintWriter(client.getOutputStream(), true);
                            InputStreamReader inputStreamReader = new InputStreamReader(client.getInputStream());
                            bufferedReader = new BufferedReader(inputStreamReader);

                            String message = "reg;" + myID + ";";
                            printwriter.write(message + "\n");
                            printwriter.flush();

                            lastAckTime = System.currentTimeMillis();
                            isConnected = true;
                        } else {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastAckTime > MAX_INACTIVITY) {
                                outerMessage = "Disconnected from server";
                                client = null;
                                printwriter = null;
                                bufferedReader = null;
                                isConnected = false;
                                mAnalyticsTracker.send(new HitBuilders.EventBuilder()
                                        .setCategory("Integration")
                                        .setAction("Server Connection Lost")
                                        .build());
                            }
                        }

                        if (previousConnectionStatus != isConnected) {
                            LogFromBackgroundThread(outerMessage);
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    mServerConnectionStatus.setChecked(isConnected);
                                }
                            });
                        }
                    }
				}
				catch (UnknownHostException e)
				{
                    LogFromBackgroundThread("Failed connect to server - unknown host!" + e.getMessage());
				}
				catch (IOException e)
				{
                    LogFromBackgroundThread("Failed connect to server!" + e.getMessage());
				}
                catch (Exception e)
                {
                    LogFromBackgroundThread("Severely failed connect to server!" + e.getMessage());
                }

				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException ie)
				{
				}
			}
		}
    }

    private class Receiver implements Runnable
    {

		private String message;
		String IP;
		double []xy;
        int color;
        double radius = 3.0;
        LatLng alienCenter;

		@Override
        public void run()
        {
			while (true) {
				try {
                    if (client == null || bufferedReader == null)
                        continue;

					if (!bufferedReader.ready())
                        continue;

                    message = null;
                    // by design - we are going to wait only SOCKET TIMEOUT (1000) here to allow bufferedReader been updated upon reconnection
                    try
                    {
                        message = bufferedReader.readLine();
                    }
                    catch (java.net.SocketTimeoutException e)
                    {
                    }
                    if (null == message)
                        continue;

                    // Process message
                    int len = message.length();
                    ///////////////////////////////////////
                    int startPos = 0;
                    int endPos = message.indexOf(';');
                    if (-1 == endPos)
                    {
                        LogFromBackgroundThread("Wrong message format");
                        continue;
                    }

                    String messageType = message.substring(startPos, endPos);
                    if (messageType.equals("DNG")) {
                        // Issue warning

                        // retrieve warning level
                        startPos = endPos + 1;
                        if (startPos >= len)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        endPos = message.indexOf(';', startPos);
                        if (-1 == endPos)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        int dangerLevel = Integer.parseInt(message.substring(startPos, endPos));
                        // 1 - low danger; 2 - high danger

                        // retrieve foe alien type
                        startPos = endPos + 1;
                        if (startPos >= len)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        endPos = message.indexOf(';', startPos);
                        if (-1 == endPos)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        int foeAlienTypeInt = Integer.parseInt(message.substring(startPos, endPos));
                        AlienType foeAlienType = AlienType.values()[foeAlienTypeInt];

                        // handling sound separately hear to make it faster
                        Thread alertingThread = new Thread(new AlertHandler(dangerLevel, foeAlienType));
                        alertingThread.start();

                        Intent startIntent = new Intent(getApplicationContext(), CollisionAlarmActivity.class);
                        //        startIntent.putExtra("com.real.MESSAGE", "You are are in danger!!!!!!");
                        startActivity(startIntent);
                        lastAckTime = System.currentTimeMillis();
                        mAnalyticsTracker.send(new HitBuilders.EventBuilder()
                                .setCategory("Integration")
                                .setAction("Collision Alarm")
                                .build());

                        continue;
                    }
                    else if (messageType.equals("ACK")) {
                        //LogFromBackgroundThread("Received ack");
                        lastAckTime = System.currentTimeMillis();
                        continue;
                    }
                    else if (messageType.equals("OFS")) {
                        ///////////////////////////////////////
                        // X
                        startPos = endPos + 1;
                        if (startPos >= len)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        endPos = message.indexOf(';', startPos);
                        if (-1 == endPos)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        String field = message.substring(startPos, endPos);
                        if (null == mOffsets)
                            mOffsets = new double[2];

                        mOffsets[0] = Double.parseDouble(field);


                        ///////////////////////////////////////
                        // Y
                        startPos = endPos + 1;
                        if (startPos >= len)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        endPos = message.indexOf(';', startPos);
                        if (-1 == endPos)
                        {
                            LogFromBackgroundThread("Wrong message format");
                            continue;
                        }
                        field = message.substring(startPos, endPos);
                        mOffsets[1] = Double.parseDouble(field);

                        lastAckTime = System.currentTimeMillis();

                        runOnUiThread(new Runnable() {
                            public void run() {
                                AlertDialog.Builder builder = new AlertDialog.Builder(AliensAppActivity.this);
                                builder.setMessage("latitude offset: " + mOffsets[0] + "\nlongitude offset: " + mOffsets[1])
                                        .setTitle("Received offsets")
                                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // User clicked OK button
                                            }
                                        })
                                        .setIcon(android.R.drawable.ic_dialog_map)
                                        .show();
                            }
                        });
                        continue;
                    }
                    else if (!messageType.equals("PRX")) {
                        LogFromBackgroundThread("Wrong message format");
                        continue;
                    }

                    lastAckTime = System.currentTimeMillis();

					if (lastLocations == null || lastLocations.size() != NUMBER_LOCATIONS_FOR_BEARING_CALCULATION)
						continue; // sanity, should not occur since we will not update server before we have enough locations anyway

					// TODO - future work - remove old aliens from the google map
					final HashMap<String, CircleOptions> aliensCircleOptions = new HashMap<>();

					// loop through aliens and add them to map
                    startPos = endPos + 1;
					if (startPos >= len)
                    {
                        LogFromBackgroundThread("Wrong message format");
                        continue;
                    }
					do {
						///////////////////////////////////////
						// IP
						endPos = message.indexOf(';', startPos);
						if (-1 == endPos)
						{
							LogFromBackgroundThread("Wrong message format");
							continue;
						}
						IP = message.substring(startPos, endPos);


						///////////////////////////////////////
						// X
						startPos = endPos + 1;
						if (startPos >= len)
						{
							LogFromBackgroundThread("Wrong message format");
							continue;
						}
						endPos = message.indexOf(';', startPos);
						if (-1 == endPos)
						{
							LogFromBackgroundThread("Wrong message format");
							continue;
						}
						String field = message.substring(startPos, endPos);
						if (null == xy)
							xy = new double[2];

						xy[0] = Double.parseDouble(field);


						///////////////////////////////////////
						// Y
						startPos = endPos + 1;
						if (startPos >= len)
						{
							LogFromBackgroundThread("Wrong message format");
							continue;
						}
						endPos = message.indexOf(';', startPos);
						if (-1 == endPos)
						{
							LogFromBackgroundThread("Wrong message format");
							continue;
						}
						field = message.substring(startPos, endPos);
						xy[1] = Double.parseDouble(field);

						LocationProjection currentLP = lastLocations.get(NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 1);
						double distanceToAlien = Geodesy.distanceBetween2d(xy, currentLP.coordXY);
						color = R.color.activity_main_label_friend;
						if (distanceToAlien < MAX_DANGER_DISTANCE)
							color = R.color.activity_main_label_dng;
						else if (distanceToAlien < MAX_FOE_DISTANCE)
							color = R.color.activity_main_label_foe;

						GlobalCoordinates geoCoordinates = Geodesy.projectMercator2dToGeodetic(xy);
						alienCenter = new LatLng(geoCoordinates.getLatitude(), geoCoordinates.getLongitude());

						radius = 3;
						if (zoomLevel > 18)
							radius = 1 / (zoomLevel - 18);
						else if (zoomLevel < 18)
							radius = 3 * (19 - zoomLevel);

						CircleOptions alienCircleOptions = new CircleOptions()
								.center(alienCenter)
								.radius(radius)
								.fillColor(getResources().getColor(color))
								.strokeColor(getResources().getColor(color))
								.strokeWidth(1.0f)
								.visible(true);

						aliensCircleOptions.put(IP, alienCircleOptions);

						startPos = endPos + 1;
					}
					while (startPos < len);

                    runOnUiThread(new Runnable() {
                        public void run() {
							Iterator<Map.Entry<String, CircleOptions>> it = aliensCircleOptions.entrySet().iterator();
							while (it.hasNext()) {
								Map.Entry<String, CircleOptions> currentEntry = it.next();
								String currentAlienIP = currentEntry.getKey();
								CircleOptions currentAlienCircleOptions = currentEntry.getValue();

								Circle currentCircle;
								if (aliens.containsKey(currentAlienIP)) {
									currentCircle = aliens.get(currentAlienIP);
								} else {
									// this has to be executed on the main (UI) thread
									currentCircle = mGoogleMap.addCircle(currentAlienCircleOptions);
									aliens.put(currentAlienIP, currentCircle);
								}

								currentCircle.setCenter(currentAlienCircleOptions.getCenter());
								currentCircle.setRadius(currentAlienCircleOptions.getRadius());
								currentCircle.setFillColor(currentAlienCircleOptions.getFillColor());
								currentCircle.setStrokeColor(currentAlienCircleOptions.getStrokeColor());
								currentCircle.setVisible(true);
							}
                        }
                    });
				} catch (Exception e) {
					LogFromBackgroundThread("Failed while processing a message");
				}

                AliensAppLogger.Log(message);

				/* no need to sleep here, since readline above is waiting for input anyway
				try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {
				}*/
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

			case R.id.action_my_id:
				final String currentMessage = "My ID: " + myID + "\n";
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(AliensAppActivity.this, currentMessage, Toast.LENGTH_SHORT).show();
                    }
                });
				return true;

            case R.id.action_change_map:
                if (null != mGoogleMap) {
                    mGoogleMap.setMapType((mGoogleMap.getMapType() == GoogleMap.MAP_TYPE_HYBRID) ? GoogleMap.MAP_TYPE_NORMAL : GoogleMap.MAP_TYPE_HYBRID);
                }
                return true;

            case R.id.action_clear_map:
                if (null != mGoogleMap) {
                    mGoogleMap.clear();

                    mRawPath = mGoogleMap.addPolyline(new PolylineOptions()
                            .width(5)
                            .color(Color.RED)
                    );
                    mFilteredPath = mGoogleMap.addPolyline(new PolylineOptions()
                            .width(5)
                            .color(Color.BLUE)
                    );
                    mCorrectedPath = mGoogleMap.addPolyline(new PolylineOptions()
                            .width(5)
                            .color(Color.GREEN)
                    );
                }
                return true;

            case R.id.action_record:
                Intent startRecordActivity = new Intent(getApplicationContext(), RecordActivity.class);
                startActivity(startRecordActivity);
                return true;

            case R.id.action_reset_offsets:
                if (printwriter != null) {
                    String message = "resetoffsets;";
                    // not sure wrapper is needed here
                    new PrintWriterFromUITask().execute(message);
                }
                mOffsets[0] = 0;
                mOffsets[1] = 0;
                return true;

            case R.id.action_settings:
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                return true;

            case R.id.action_test_warning:
                if (printwriter != null) {
                    String message = "test;";
                    // not sure wrapper is needed here
                    new PrintWriterFromUITask().execute(message);
                }
                return true;

            default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (null != mMapView)
			mMapView.onResume();

		// Request location updates with the highest possible frequency on gps.
		// Typically, this means one update per second for gps.

		// For the network provider, which gives locations with less accuracy (less reliable),
		// request updates every 5 seconds.

		// For the filtertime argument we use a "real" value: the predictions are triggered by a timer.
		// Lets say we want 5 updates per second = update each 200 millis.

/*		if (null != mKalmanLocationManager)
		{
			mKalmanLocationManager.requestLocationUpdates(
					KalmanLocationManager.UseProvider.GPS_AND_NET, FILTER_TIME, GPS_TIME, NET_TIME, mLocationListener, true);
		}
*/
		// Register the broadcast receiver that informs this activity of the DetectedActivity
		// object broadcast sent by the intent service.
		LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION));

		/* ACCELERATION-SENSOR -> registerSensorManagerListeners(); <- ACCELERATION-SENSOR */
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (null != mMapView)
			mMapView.onPause();

		// Remove location updates
/*		if (null != mKalmanLocationManager)
			mKalmanLocationManager.removeUpdates(mLocationListener);
*/
		// Store zoom level
		if (null != mPreferences  &&  null != mGoogleMap)
            mPreferences.edit().putFloat("zoom", mGoogleMap.getCameraPosition().zoom).apply();

		// Unregister the broadcast receiver that was registered during onResume().
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);

		/* ACCELERATION-SENSOR -> sensorManager.unregisterListener(this); <- ACCELERATION-SENSOR */
    }

    // Main logic of location calculation
    private void HandleLocationUpdate(Location location)
    {
		try
		{
	/*        // GPS location
			if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {

				LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
				mGpsCircle.setCenter(latLng);
				mGpsCircle.setRadius(location.getAccuracy());
				mGpsCircle.setVisible(true);

				lastGPSLocation = location;
				return;
			}

			// Network location
			if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {

				LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
				mNetCircle.setCenter(latLng);
				mNetCircle.setRadius(location.getAccuracy());
				mNetCircle.setVisible(true);
				return;
			}
	*/
	/*
			if (!location.getProvider().equals(KalmanLocationManager.KALMAN_PROVIDER)  ||  mOnLocationChangedListener == null)
				return;

			// If Kalman location and google maps activated the supplied mLocationSource

			LatLng latLng1 = new LatLng(location.getLatitude(), location.getLongitude());
			mKalmanCircle.setCenter(latLng1);
			mKalmanCircle.setRadius(3.0);
			mKalmanCircle.setVisible(true);
	*/
			if (mOnLocationChangedListener == null)
				return;

/*        if (null == lastGPSLocation)
        {
            final String message1 = "No GPS signal, system is not functioning...\n";
            LogFromBackgroundThread(message1);
            return;
        }
*/
            // TODO: replace getTime() by getElapsedRealtimeNanos() (requires upgrade of minimum supported API level
            String event = "event;SENSORS;" + String.valueOf(location.getTime()) + ";" +
                            String.valueOf(location.getLatitude()) + ";" + String.valueOf(location.getLongitude()) + ";" + String.valueOf(location.getBearing()) + ";" +
                            String.valueOf(location.getAccuracy()) + ";" + String.valueOf(location.getAltitude());
            if (SENSOR_TESTING_MODE)
            {
                AliensAppLogger.Log(event);
            }
            else
            {
                new PrintWriterFromUITask().execute(event);
            }

			// Ignore too rough locations - based on accuracy first
			float accuracy = location.getAccuracy();
			if (accuracy > ACCURACY_THRESHOLD)
			{
                if (!SENSOR_TESTING_MODE) {
                    //final String message1 = "Accuracy is too bad, system is not functioning...\n";
                    //LogFromBackgroundThread(message1); // not issuing to UI as proved to happen from time to time with Google Play services location (jumps from 10 to 96)
                    final String message1 = "Skipping update with bad accuracy: " + accuracy;
                    AliensAppLogger.Log(message1);
                    return;
                }
			}

            boolean isCoherentData = calculateBearingAndSpeedBasedOnLastLocations(location);
            if (!isCoherentData)
            {
                if (!SENSOR_TESTING_MODE)
                {
                    final String message1 = "Skipping update with wrong velocity";
                    AliensAppLogger.Log(message1);
                    return;
                }
            }

			if (lastLocations.size() == NUMBER_LOCATIONS_FOR_BEARING_CALCULATION) {

				boolean shouldUpdateServer = true;
				final LocationProjection currentLP = lastLocations.get(NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 1);
				// TODO: next if should be commented out if we want server to be updated as soon as possible
				if (location.getTime() - lastUpdateTime < MIN_SERVER_UPDATE_TIME)
				{
					shouldUpdateServer = false; // avoid too frequent updates
				}
				else
				{
					LocationProjection prevLP = lastLocations.get(NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 2);
					if (prevLP.velocity == 0.0f  &&  currentLP.velocity == 0.0f) {

						if (null != lastLocation) {
							double distanceToLast = location.distanceTo(lastLocation);

							// no point to make frequent updates if object does not move
							if (distanceToLast < MAX_LOCATION_ACC) {
								shouldUpdateServer = false;
							}
						}

						// even if not changed location, still have to let server know we are alive
						// tricky - server will ignore objects, which do not update for more than 10 sec
						if (!shouldUpdateServer && location.getTime() - lastUpdateTime > MAX_SERVER_NO_UPDATE_TIME)
							//if (location.getElapsedRealtimeNanos() - lastUpdateTime > 5 * NANO_SEC_IN_SEC)
							shouldUpdateServer = true;
					}
				}

                if (!SENSOR_TESTING_MODE)
                {
                    // update server
                    if (printwriter != null && shouldUpdateServer) {
                        //lastUpdateTime = location.getElapsedRealtimeNanos();
                        lastUpdateTime = location.getTime();

                        String X = String.valueOf(currentLP.coordXY[0]);
                        String Y = String.valueOf(currentLP.coordXY[1]);
                        String A = String.valueOf(currentLP.A);
                        String B = String.valueOf(currentLP.B);
                        String dir = String.valueOf(currentLP.direction);
                        String spd = String.valueOf(currentLP.velocity);
                        String message = "upd;" + X + ";" + Y + ";" + A + ";" + B + ";" + dir + ";" + spd + ";" + ownType.ordinal() + ";";
                        new PrintWriterFromUITask().execute(message);
                    }
                }

                final Location rawLocation = new Location(location); // used in sensor testing mode only

                GlobalCoordinates geoCoordinates = Geodesy.projectMercator2dToGeodetic(currentLP.coordXY);
                location.setLatitude(geoCoordinates.getLatitude());
                location.setLongitude(geoCoordinates.getLongitude());

                // not clear, if this really needed - anyway this is only for representation on host device -
                // should not be done too frequently for pedestrian, cause this makes map jumping from one orientation to another on every update
                final LocationProjection oldestLP = lastLocations.get(0);
                if (0 == oldestLP.velocity  &&  0 == currentLP.velocity) {
                    GlobalCoordinates oldestCoordinates = Geodesy.projectMercator2dToGeodetic(oldestLP.coordXY);
                    float currentBearing = doubleToFloatSafe(Geodesy.bearingTo(oldestCoordinates, geoCoordinates));
                    location.setBearing(currentBearing);
                }

                String calculatedLocationToLog = "Calculated Location;" +
                        String.valueOf(location.getTime()) + ";" +
                        String.valueOf(location.getLatitude()) + ";" + String.valueOf(location.getLongitude()) + ";" + String.valueOf(location.getBearing()) + ";" +
                        String.valueOf(location.getAccuracy()) + ";" + ownType.ordinal();
                AliensAppLogger.Log(calculatedLocationToLog);

                lastLocation = location;

                runOnUiThread(new Runnable() {
                    public void run() {
                        // Update blue "myLocation" dot
                        mOnLocationChangedListener.onLocationChanged(rawLocation);

                        zoomLevel = mGoogleMap.getCameraPosition().zoom;

                        if (!cameraInitialized) {
                            cameraInitialized = true;

                            // update camera position
                            LatLng latLng = new LatLng(rawLocation.getLatitude(), rawLocation.getLongitude());
                            CameraPosition.Builder positionBuilder = CameraPosition.builder(mGoogleMap.getCameraPosition());
                            positionBuilder.target(latLng);
                            if (currentLP.velocity != 0.0f)
                                positionBuilder.bearing(rawLocation.getBearing());

                            CameraPosition position = positionBuilder.build();
                            CameraUpdate update = CameraUpdateFactory.newCameraPosition(position);
                            mGoogleMap.animateCamera(update, (int) FILTER_TIME, null);
                        }

                        if (null == mRawPath) {
                            mRawPath = mGoogleMap.addPolyline(new PolylineOptions()
                                    .width(5)
                                    .color(Color.RED)
                            );
                            mFilteredPath = mGoogleMap.addPolyline(new PolylineOptions()
                                    .width(5)
                                    .color(Color.BLUE)
                            );
                            mCorrectedPath = mGoogleMap.addPolyline(new PolylineOptions()
                                    .width(5)
                                    .color(Color.GREEN)
                            );
                        }
                        List<LatLng> points1 = mRawPath.getPoints();
                        points1.add(new LatLng(rawLocation.getLatitude(), rawLocation.getLongitude()));
                        mRawPath.setPoints(points1);

                        List<LatLng> points2 = mFilteredPath.getPoints();
                        points2.add(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()));
                        mFilteredPath.setPoints(points2);
                        if (null != mOffsets && (mOffsets[0] != 0 || mOffsets[1] != 0)) {
                            List<LatLng> points3 = mCorrectedPath.getPoints();
                            double []corrected = {currentLP.coordXY[0] + mOffsets[0], currentLP.coordXY[1] + mOffsets[1]};
                            GlobalCoordinates geoCoordinates = Geodesy.projectMercator2dToGeodetic(corrected);
                            points3.add(new LatLng(geoCoordinates.getLatitude(), geoCoordinates.getLongitude()));
                            mCorrectedPath.setPoints(points3);
                        }
                    }
                });
			}
		} catch (ArithmeticException ex) {
			LogFromBackgroundThread(String.format("ArithmeticException caught" + ex.toString()));
		}
	}

    // from: http://www.cleverstudents.ru/line_and_plane/projection_of_point_onto_line.html
    // given line formula y = Ax + B, or Ax - y + B = 0, and a point (a, b) to project on the line:
    // projection point (px, py) will be defined as follows:
    //  px = (-A(B - b) + a) : (1 + A*A)
    //  py = Ax + B
    // (in case p does not belong to line; this test is done with accuracy of 1cm - should be sufficient for our needs)
    //
    // arguments
    //  lp - line definition by LocationProjection.A/B
    //  p - point to project
    //
    // returns
    //  in case p already belongs to the line - p is not changed
    //  otherwise p will hold the projected point
    private void calculatePointProjectionOnLine(LocationProjection lp, double []p) {

        // check if p belongs to the line
        double yLine = lp.A * p[0] + lp.B;
        if (Math.abs(yLine - p[1]) < 0.01)
            return;

        p[0] = (-lp.A * (lp.B - p[1]) + p[0]) / (1 + Math.pow(lp.A, 2));
        p[1] = lp.A * p[0] + lp.B;
    }

    private boolean calculateBearingAndSpeedBasedOnLastLocations(Location location)
    {
        // convert geodetic coordinates to 2d
        LocationProjection currentInformation = new LocationProjection();
        GlobalCoordinates geodetic = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
        Geodesy.projectMercatorGeodeticTo2d(geodetic, currentInformation.rawXY);
        currentInformation.coordXY = currentInformation.rawXY;
        //currentInformation.timestamp = location.getElapsedRealtimeNanos();
        currentInformation.timestamp = location.getTime();
        currentInformation.X2 = Math.pow(currentInformation.coordXY[0], 2);
        currentInformation.XY = currentInformation.coordXY[0] * currentInformation.coordXY[1];
        currentInformation.A = location.getBearing();
        currentInformation.velocity = location.getSpeed();

        if (null != mDetectedActivities  &&  ownType == AlienType.VEHICLE) {
            // ignore not reasonable drops in vehicle velocity, based on stopping distance calculation formula
            // see http://www.csgnetwork.com/stopdistcalc.html
            //      http://www.csgnetwork.com/stopdistinfo.html
            //      http://www.softschools.com/formulas/physics/stopping_distance_formula/89/
            // we assume that normally velocity can decrease at rate of 4.572 m/s (15 feet/s)
            if (null != lastLocations && lastLocations.size() > 0) {
                LocationProjection prevLP = lastLocations.get(lastLocations.size() - 1);
                float timeDelta = (currentInformation.timestamp - prevLP.timestamp) / 1000; // sec
                if (prevLP.velocity > LOW_SPEED  &&  prevLP.velocity > currentInformation.velocity  &&
                        BRAKING_BAD * timeDelta < prevLP.velocity - currentInformation.velocity) {
                    AliensAppLogger.Log("Ignored not reasonable drops in vehicle velocity, based on stopping distance calculation formula");
                    return false;
                }
            }
        }

        int lastRelevantIndex = 0;
        if (currentInformation.velocity > HIGH_SPEED)
        {
            currentInformation.velocity_group = RACE_SPEED_GROUP;
            lastRelevantIndex = NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 1;
        }
        else if (currentInformation.velocity > AVERAGE_SPEED)
        {
            currentInformation.velocity_group = HIGH_SPEED_GROUP;
            lastRelevantIndex = NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 2;
        }
        else if (currentInformation.velocity > LOW_SPEED)
        {
            currentInformation.velocity_group = AVERAGE_SPEED_GROUP;
            lastRelevantIndex = NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 3;
        }
        else if (currentInformation.velocity > MIN_NON_ZERO_SPEED)
        {
            currentInformation.velocity_group = LOW_SPEED_GROUP;
        }
        else
        {
            currentInformation.velocity = 0.0f;
        }

        // if detected activity is STILL - we ignore GPS information, except for big jumps
        if (null != mDetectedActivities) {

            if (mDetectedActivities.get(MonitoredActivitiesIndex.STILL.ordinal()).getConfidence() > 80) {
                // we are in STILL case, however if the change in location is too big - we will not trust detected activity -
                // it takes few seconds to correctly adjust the detected activity, we can't wait for so long - for example,
                // in case of high acceleration
                boolean bigJump = false;
                if (null != lastLocations && lastLocations.size() > 0) {
                    LocationProjection prevLP = lastLocations.get(lastLocations.size() - 1);
                    double distanceToLast = Geodesy.distanceBetween2d(prevLP.coordXY, currentInformation.coordXY);
                    if (location.getAccuracy() + lastLocation.getAccuracy() < distanceToLast) {
                        AliensAppLogger.Log("Big jump detected in STILL activity");
                        bigJump = true;
                    }
                }

                if (!bigJump) {
                    currentInformation.velocity = 0.0f;
                    currentInformation.velocity_group = ZERO_SPEED_GROUP;
                    //AliensAppLogger.Log("Detected STILL object");
                }
            }
            else if (mDetectedActivities.get(MonitoredActivitiesIndex.IN_VEHICLE.ordinal()).getConfidence() > 80 ||
                    mDetectedActivities.get(MonitoredActivitiesIndex.ON_BICYCLE.ordinal()).getConfidence() > 80) {
                ownType = AlienType.VEHICLE;
                AliensAppLogger.Log("Detected VEHICLE based on high confidence of API");
            }
        }
        if (currentInformation.velocity > USEIN_BOLT)
        {
            ownType = AlienType.VEHICLE;
            AliensAppLogger.Log("Detected VEHICLE based on calculated velocity");
        }

        if (null == lastLocations) {
            lastLocations = new LinkedList<>();
        }

        if (lastLocations.size() == 0)
        {
            currentInformation.SUMX = currentInformation.rawXY[0];
            currentInformation.SUMY = currentInformation.rawXY[1];
            currentInformation.SUMX2 = currentInformation.X2;
            currentInformation.SUMXY = currentInformation.XY;
        }
        else if (lastLocations.size() < NUMBER_LOCATIONS_FOR_BEARING_CALCULATION)
        {
            LocationProjection newestInformation = lastLocations.get(lastLocations.size() - 1);

            currentInformation.SUMX = newestInformation.SUMX + currentInformation.rawXY[0];
            currentInformation.SUMY = newestInformation.SUMY + currentInformation.rawXY[1];
            currentInformation.SUMX2 = newestInformation.SUMX2 + currentInformation.X2;
            currentInformation.SUMXY = newestInformation.SUMXY + currentInformation.XY;
        }
        else // we have enough information to build the approximation
        {
            // calculate best fit line for the last location information
            LocationProjection oldestInformation = lastLocations.get(0);
            LocationProjection newestInformation = lastLocations.get(NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 1);

            currentInformation.SUMX = newestInformation.SUMX + currentInformation.rawXY[0] - oldestInformation.rawXY[0];
            currentInformation.SUMY = newestInformation.SUMY + currentInformation.rawXY[1] - oldestInformation.rawXY[1];
            currentInformation.SUMX2 = newestInformation.SUMX2 + currentInformation.X2 - oldestInformation.X2;
            currentInformation.SUMXY = newestInformation.SUMXY + currentInformation.XY - oldestInformation.XY;

            if (ZERO_SPEED_GROUP == currentInformation.velocity_group  ||  LOW_SPEED_GROUP == currentInformation.velocity_group)
            {
                double XMean = currentInformation.SUMX / NUMBER_LOCATIONS_FOR_BEARING_CALCULATION;
                double YMean = currentInformation.SUMY / NUMBER_LOCATIONS_FOR_BEARING_CALCULATION;

                // line formula (y = slope * x + yint)
                double div = currentInformation.SUMX2 - currentInformation.SUMX * XMean;
                if (0.0 == div)
                    div = 0.0000001; // arbitrary small to get high slope. this is the case of line of formula: x = XMean
                double Slope = (currentInformation.SUMXY - currentInformation.SUMX * YMean) / div;
                double YInt = YMean - Slope * XMean;

                currentInformation.A = Slope;
                currentInformation.B = YInt;

                // handle direction
                if (ZERO_SPEED_GROUP == currentInformation.velocity_group)
    				currentInformation.direction = oldestInformation.coordXY[0] < currentInformation.coordXY[0];
                else
                    currentInformation.direction = newestInformation.coordXY[0] < currentInformation.coordXY[0];

                // in case of not moving - double check it - find distance covered during last NUMBER_LOCATIONS_FOR_BEARING_CALCULATION
                // along found above line and calculate speed. This will override above based on monitored activities decision (when STILL was detected with high confidence).
                if (ZERO_SPEED_GROUP == currentInformation.velocity_group  &&  ZERO_SPEED_GROUP == oldestInformation.velocity_group) {
                    float timeDelta = (currentInformation.timestamp - oldestInformation.timestamp) / 1000; // sec

                    double []oldestProjected = oldestInformation.rawXY;
                    calculatePointProjectionOnLine(currentInformation, oldestProjected);

                    calculatePointProjectionOnLine(currentInformation, currentInformation.coordXY);

                    double distanceToOldest = Geodesy.distanceBetween2d(oldestProjected, currentInformation.coordXY);
                    currentInformation.velocity = distanceToOldest / timeDelta;
                    // even if real velocity is not zero, for our needs it does not matter once it is less than MIN_NON_ZERO_SPEED -
                    // such objects can be treated as STILL
                    if (currentInformation.velocity <= MIN_NON_ZERO_SPEED)
                        currentInformation.velocity = 0.0f;
                    else
                        currentInformation.velocity_group = LOW_SPEED_GROUP;
                }
            }
            else
            {
                LocationProjection lastRelevant = lastLocations.get(lastRelevantIndex);

                // line formula (y = slope * x + yint)
                double div = currentInformation.coordXY[0] - lastRelevant.coordXY[0];
                if (0.0 == div)
                    div = 0.0000001; // arbitrary small to get high slope. this is the case of line of formula: x = XMean

                double Slope = (currentInformation.coordXY[1] - lastRelevant.coordXY[1]) / div;
                double YInt = currentInformation.coordXY[1] - Slope * currentInformation.coordXY[0];

                currentInformation.A = Slope;
                currentInformation.B = YInt;
				currentInformation.direction = lastRelevant.coordXY[0] < currentInformation.coordXY[0];
            }

            lastLocations.remove(0);
        }

        lastLocations.add(currentInformation);

        return true;
    }

    /**
     * Listener used to get updates from KalmanLocationManager (the good old Android LocationListener).
     */
/*    private android.location.LocationListener mLocationListener = new android.location.LocationListener() {

        //Circle alienCircle;

        @Override
        public void onLocationChanged(Location location) {
            HandleLocationUpdate(location);
        }

        @Override
		public void onStatusChanged(String provider, int status, Bundle extras) {

			String statusString = "Unknown";

			switch (status) {

				case LocationProvider.OUT_OF_SERVICE:
					statusString = "Out of service";
					break;

				case LocationProvider.TEMPORARILY_UNAVAILABLE:
					statusString = "Temporary unavailable";
					break;

				case LocationProvider.AVAILABLE:
					statusString = "Available";
					break;
			}

			String message1 = new String();
			message1.format("Provider '%s' status: %s", provider, statusString);
			LogFromBackgroundThread(message1);

		}

		@Override
		public void onProviderEnabled(String provider) {

			String message1 = new String();
			message1.format("Provider '%s' enabled", provider);
			LogFromBackgroundThread(message1);
		}

		@Override
		public void onProviderDisabled(String provider) {

			String message1 = new String();
			message1.format("Provider '%s' disabled", provider);
			LogFromBackgroundThread(message1);
		}
	};
*/

	/**
	 * Location Source for google maps 'my location' layer.
	 */
	private LocationSource mLocationSource = new LocationSource() {

		@Override
		public void activate(OnLocationChangedListener onLocationChangedListener) {

			mOnLocationChangedListener = onLocationChangedListener;
		}

		@Override
		public void deactivate() {

			mOnLocationChangedListener = null;
		}
	};



	// Google Play Services API
	/**
	 * Runs when the result of calling requestActivityUpdates() and removeActivityUpdates() becomes
	 * available. Either method can complete successfully or with an error.
	 *
	 * @param status The Status returned through a PendingIntent when requestActivityUpdates()
	 *               or removeActivityUpdates() are called.
	 */
	public void onResult(Status status) {
		if (status.isSuccess()) {
			// Toggle the status of activity updates requested, and save in shared preferences.
			boolean requestingUpdates = !getUpdatesRequestedState();
			setUpdatesRequestedState(requestingUpdates);
		} else {
			LogFromBackgroundThread("Error adding or removing activity detection: " + status.getStatusMessage());
		}
	}

	/**
	 * Gets a PendingIntent to be sent for each activity detection.
	 */
	private PendingIntent getActivityDetectionPendingIntent() {

		Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

		// We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
		// requestActivityUpdates() and removeActivityUpdates().
		return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}


	/**
	 * Retrieves a SharedPreference object used to store or read values in this app. If a
	 * preferences file passed as the first argument to {@link #getSharedPreferences}
	 * does not exist, it is created when {@link SharedPreferences.Editor} is used to commit
	 * data.
	 */
	private SharedPreferences getSharedPreferencesInstance() {
		return getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
	}

	/**
	 * Retrieves the boolean from SharedPreferences that tracks whether we are requesting activity
	 * updates.
	 */
	private boolean getUpdatesRequestedState() {
		return getSharedPreferencesInstance()
				.getBoolean(ACTIVITY_UPDATES_REQUESTED_KEY, false);
	}

	/**
	 * Sets the boolean in SharedPreferences that tracks whether we are requesting activity
	 * updates.
	 */
	private void setUpdatesRequestedState(boolean requestingUpdates) {
		getSharedPreferencesInstance()
				.edit()
				.putBoolean(ACTIVITY_UPDATES_REQUESTED_KEY, requestingUpdates)
				.commit();
	}

	/**
	 * Stores the list of detected activities in the Bundle.
	 */
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putSerializable(DETECTED_ACTIVITIES, mDetectedActivities);
		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
	 * Receives a list of one or more DetectedActivity objects associated with the current state of
	 * the device.
	 */
	public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
		protected static final String TAG = "activity-detection-response-receiver";

		@Override
		public void onReceive(Context context, Intent intent) {
			ArrayList<DetectedActivity> updatedActivities = intent.getParcelableArrayListExtra(ACTIVITY_EXTRA);

			HashMap<Integer, Integer> detectedActivitiesMap = new HashMap<>();
			for (DetectedActivity activity : updatedActivities) {
				detectedActivitiesMap.put(activity.getType(), activity.getConfidence());
                AliensAppLogger.Log("Detected activity: " + activity.getType() + " with confidence: " + activity.getConfidence());
			}
			// Every time we detect new activities, we want to reset the confidence level of ALL
			// activities that we monitor. Since we cannot directly change the confidence
			// of a DetectedActivity, we use a temporary list of DetectedActivity objects. If an
			// activity was freshly detected, we use its confidence level. Otherwise, we set the
			// confidence level to zero.
			for (int i = 0; i < MONITORED_ACTIVITIES.length; i++) {
				int confidence = detectedActivitiesMap.containsKey(MONITORED_ACTIVITIES[i])? detectedActivitiesMap.get(MONITORED_ACTIVITIES[i]) : 0;
				mDetectedActivities.set(i, new DetectedActivity(MONITORED_ACTIVITIES[i], confidence));
			}
		}
	}

/*    private void playSound()
    {
        if (mediaPlayer == null)
            return;

        // we should initialize media player in advance to make playback faster,
        // for that need to determine when resources are available already
        mediaPlayer = MediaPlayer.create(this, R.raw.annoying_alarm_clock); // "create" handles "prepare" too in this case

        // TODO - acquire focus (https://developer.android.com/guide/topics/media/mediaplayer.html)
        mediaPlayer.start();

        Thread thread = new Thread() {
            public void run() {
                Looper.prepare();

                try {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mediaPlayer.stop();
                                mediaPlayer.prepare(); // preparing player for next time
                            } catch (IOException ioe) {
                                LogFromBackgroundThread("Exception " + ioe.toString());
                            } finally {
                                handler.removeCallbacks(this);
                                Looper.myLooper().quit();
                            }
                        }
                    }, 3000);
                } catch (Exception e){
                    LogFromBackgroundThread("Exception " + e.toString());
                }

                Looper.loop();
            }
        };
        thread.start();
    }*/

    private void HandleCheckPoint(LatLng markerLocation)
    {
        try
        {
            if (printwriter != null) {

                if (lastLocations == null || lastLocations.size() != NUMBER_LOCATIONS_FOR_BEARING_CALCULATION)
                    return;

                // find offset with the given checkpoint
                GlobalCoordinates checkpointGeodetic = new GlobalCoordinates(markerLocation.latitude, markerLocation.longitude);
                double []checkpointXY = new double[2];
                Geodesy.projectMercatorGeodeticTo2d(checkpointGeodetic, checkpointXY);
                LocationProjection currentLP = lastLocations.get(NUMBER_LOCATIONS_FOR_BEARING_CALCULATION - 1);
                double []offsetsXY = new double[2];
                offsetsXY[0] = checkpointXY[0] - currentLP.coordXY[0];
                offsetsXY[1] = checkpointXY[1] - currentLP.coordXY[1];

                String message = "chkpnt;" + offsetsXY[0] + ";" + offsetsXY[1] + ";";

                // not sure wrapper is needed here
                new PrintWriterFromUITask().execute(message);
            }

        } catch (Exception e){
            LogFromBackgroundThread("Exception " + e.toString());
        }
    }
}
