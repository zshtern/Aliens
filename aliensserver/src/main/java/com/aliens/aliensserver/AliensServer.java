package com.aliens.aliensserver;

import org.gavaghan.geodesy.Geodesy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

//import static android.R.attr.y;

// import static android.R.id.message;

public class AliensServer {

    private static final long MAX_DISTANCE_TO_CONSIDER_SAME_VEHICLE = 5; // meters
    private static final double MAX_SPEED_DELTA_TO_CONSIDER_SAME_VEHICLE = 2.0; // m/s
    private static final double MAX_DIR_DELTA_TO_CONSIDER_SAME_VEHICLE = Math.tan(Math.PI/10); // 18 degrees
    private static final long MAX_TIME_TO_LEAVE_RIDE = 8000; // millis

    private static final long MAX_DISTANCE_TO_CHECK_COLLISION = 200; // meters - distance between two aliens to check possible collisions
    private static final long MAX_DISTANCE_TO_DECLARE_COLLISION = 5; // meters - distance between two aliens "at the collision point"
    private static final long MAX_TIME_DIFFERENCE_TO_GET_TO_COLLISION_POINT = 2000; // milli seconds - half sec difference still can be a collision
    private static final long MAX_TIME_TO_COLLISION_POINT = 7000; // milli seconds - time to get to collision point
    private static final long MAX_EMERGENCY_TIME_TO_COLLISION_POINT = 3000; // milli seconds - low emergency boundary time to get to collision point
    private static final long MIN_COLLISION_TIME_TO_ISSUE_WARNING = 2000; // milli seconds - minimal time to get to collision point that it is still worth to issue warning

    private static final long MAX_ALLOWED_INACTIVITY = 120000; // milli seconds - period to wait before remove the alien from clients
    private static final long MAX_INACTIVITY_FOR_WARNING = 10000; // milli sec - after this period no warnings are issued, since we can't be sure about the alien move
    private static final long MAX_TIME_WO_UPDATES_FROM_ALIEN = 250; // milli sec - time out to wait before updating alien location
    private static final long MAX_TIME_WO_UPDATES = 500; // milli sec - time out to wait without update from any alien before checking for collisions

    private static final long MIN_TIME_TO_UPDATE_FAR_ALIENS = 3000; // milli sec - minimal interval to update far aliens

    // 8.0 is emergency braking - based on internet research, our testing shows that moderate braking produces 4 m/s decrease in speed
    private static final double MIN_EMERGENCY_BRAKING_VELOCITY_DELTA = 3.0; // m/s

    private static final long TIMEOUT_BETWEEN_TWO_WARNINGS = 10000; // milli seconds
    private static final long TIMEOUT_BETWEEN_TWO_WARNINGS_OF_DIFFERENT_LEVEL = 2000; // milli seconds
    private static final double MIN_DANGER_VELOCITY = 3.0; // meters/sec
    private static final double MAX_ANGLE_DELTA_TO_ASSUME_SAME_DIR = Math.tan(Math.PI/10); // 18 degrees
    private static final double MAX_DISTANCE_BETWEEN_PARALLEL_PATHS = MAX_ANGLE_DELTA_TO_ASSUME_SAME_DIR * MAX_DISTANCE_TO_CHECK_COLLISION; // ~65 m
    private static final long MAX_CLIP_DURATION = 60000; // msec

    private static final String CLIENT_MESSAGE = "CLIENT-MESSAGE";
    private static final String FRONT_END_WARNING = "FRONT-END-WARNING";
    private static final String SELF_UPDATE = "SELF-UPDATE";
    // private static final String ALIEN_UPDATED = "ALIEN-UPDATED"; // redundant?
    private static final String COLLISION_WARNING = "COLLISION-WARNING";
    private static final String TESTING = "TESTING-";
    private static final String SCENARIO = "Scenario:";

    private static final String PROXIMITY_WARNING = "PRX;";
    private static final String DANGER_WARNING = "DNG;";
    private static final String NEIGHBORHOOD_MESSAGE = "NEI;"; // for future use - in showing neighbors for demo
    private static final String ACK_MESSAGE = "ACK;";
    private static final String OFFSETS_MESSAGE = "OFS;";


    private static final Logger LOGGER = Logger.getLogger(AliensServer.class.getName() );
    private static int clipCounter = 0;

    // for testing
    private static int warningLevel = 0;

    // TODO - use Collections.synchronizedMap(new HashMap(...)) to support concurrent changes from multiple threads
    // TODO - replace string key by an integer id
    private static final HashMap<String, Alien> aliens = new HashMap<>();

    // GPS offsets
    private static class Offsets {
        double []XY = new double[2];
        long time = 0;

        Offsets(double []currentXY, long currentTime) { this.XY = currentXY; this.time = currentTime; }
    }
    private static final LinkedList<Offsets> offsetsList = new LinkedList<>();
    private static final long MAX_OFFSET_LIFESPAN = 300000; // 5 min for testing, most probably could be 30 min in msec
    private static final double []offsetsSum = {0, 0};
    private static final Lock offsetsLock = new ReentrantLock();

    // Collision Hypothesis
    private static class IDsPair {
        long a;
        long b;

        IDsPair(long a, long b) { this.a = a; this.b = b; }

        public boolean equals(Object o) {
            if(o instanceof IDsPair) {
                IDsPair p = (IDsPair)o;
                if (a == p.a && b == p.b)
                    return true;

                if (a == p.b && b == p.a)
                    return true;
            }
            return false;
        }
        // poor hashcode, for sure, but we need hash(a,b) == hash(b,a)...
        public int hashCode() { return (int)(a ^ b); }
        public String toString() { return "Pair (" + a + "," + b + ")"; }
    }

    private static class CollisionHypothesis {
        String idObj1;
        double lastTimeToCollisionPointObj1;
        String idObj2;
        double lastTimeToCollisionPointObj2;
        double lastDistance;
        double originationTime = 0;

        // used to override low-dnager warning by a higher one
        int dangerLevel = 0;

        CollisionHypothesis(Alien a1, double t1, Alien a2, double t2, double distance, double originTime)
        {
            this.idObj1 = a1.ID;
            this.lastTimeToCollisionPointObj1 = t1;
            this.idObj2 = a2.ID;
            this.lastTimeToCollisionPointObj2 = t2;
            this.lastDistance = distance;
            this.originationTime = originTime;
        }

        public String toString() { return "CollisionHypothesis (" + idObj1 + "," + lastTimeToCollisionPointObj1 + "," + idObj2 + "," + lastTimeToCollisionPointObj2 + "," + lastDistance + "," + originationTime + ")"; }

/*
        boolean IsSupported(Alien a1, double t1, Alien a2, double t2, double currentTime)
        {
            if (a1.lastUpdateMessageTime <= originationTime  &&  a2.lastUpdateMessageTime <= originationTime)
            {
                LOGGER.info("hypothesis support is false - no real update occurred yet");
                return false;
            }

            boolean result = false;
            if (idObj1 == a1.ID)
            {
                if (lastTimeToCollisionPointObj1 >= t1 && lastTimeToCollisionPointObj2 >= t2  && currentTime - originationTime > 500)
                    result = true;

                lastTimeToCollisionPointObj1 = t1;
                lastTimeToCollisionPointObj2 = t2;
            }
            else if (idObj1 == a2.ID)
            {
                if (lastTimeToCollisionPointObj1 >= t2 && lastTimeToCollisionPointObj2 >= t1  && currentTime - originationTime > 500)
                    result = true;

                lastTimeToCollisionPointObj1 = t2;
                lastTimeToCollisionPointObj2 = t1;
            }

            LOGGER.info("hypothesis support is: " + result);

            return result;
        }
*/
        // checks if hypothesis is still relevant
        //      0 - danger decreased
        //      1 - unknown (no evidences to any decision)
        //      2 - danger increased
        int CheckStatus(Alien a1, double t1, Alien a2, double t2, double distance, double currentTime)
        {
            if (a1.lastUpdateMessageTime <= originationTime  &&  a2.lastUpdateMessageTime <= originationTime)
            {
                LOGGER.info("hypothesis support is false - no real update occurred yet");
                return 1;
            }

            int result = 1;
            if (lastDistance <= distance)
                return 0;

            if (idObj1.equals(a1.ID))
            {
                if (lastTimeToCollisionPointObj1 >= t1 && lastTimeToCollisionPointObj2 >= t2)//  && currentTime - originationTime > 500)
                    result = 2;
                else
                    result = 0;

                lastTimeToCollisionPointObj1 = t1;
                lastTimeToCollisionPointObj2 = t2;
            }
            else if (idObj1.equals(a2.ID))
            {
                if (lastTimeToCollisionPointObj1 >= t2 && lastTimeToCollisionPointObj2 >= t1)//  && currentTime - originationTime > 500)
                    result = 2;
                else
                    result = 0;

                lastTimeToCollisionPointObj1 = t2;
                lastTimeToCollisionPointObj2 = t1;
            }

            LOGGER.info("hypothesis support is: " + result);

            return result;
        }
    }

    // once possible collision is detected, we keep it as a candidate, until it is dis/approved by next data
    // we only check if a pair is already suspected for collision from previous iteration now
    // in future - we can check more parameters - decreasing distance etc
    // to test run TN-TwoFollowingBicycles2.clip
    private static final HashMap<IDsPair, CollisionHypothesis> collisionsHypothesis = new HashMap<>();

    // Todo - instead of keeping tail of events in memory - when a clip is requested - it can be copied from log
    private static final LinkedList<AlienEvent> alienEvents = new LinkedList<>();
    private static final Lock alienEventsLock = new ReentrantLock();

    private static long lastMessageTime = 0;

    // testing members
    private static boolean normalMode = true;
    private static String warningIssued;
    private static String currentScenario = null;
    private static long eventTime = 0;


    // GeodeticCalculator make more precise calculations (based on its implementation), but it is not required for our needs at the moment
    // private static GeodeticCalculator geodeticCalculator = new GeodeticCalculator();
    //private static Ellipsoid geodeticCalculatorEllipsoid = Ellipsoid.WGS84;
    public static void main(String[] args)
    {
        try {
            // set up own logging
            Handler fileHandler  = new FileHandler("./Aliens.log", 1024 * 1024, 1024, true);
            LOGGER.addHandler(fileHandler);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new Formatter() {
                public String format(LogRecord rec) {
                    return rec.getThreadID() + "::" + rec.getSourceClassName() + "::" + rec.getSourceMethodName() + "::" + new Date(rec.getMillis()) + "::" + rec.getMessage() + "\n";
                }
            });
            LOGGER.setLevel(Level.ALL);

            String s = args[0];
            if (s.equals("-normal"))
                RunServer();
            else if (s.equals("-testing"))
                TestServer(args[1]);
            else if (s.equals("-matlab"))
                ConvertClipToMatlab(args[1]);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }

    private static void RunServer()
    {
        try {
            normalMode = true;

            // main client communication loop
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            // priority for low latency:
            serverSocket.setPerformancePreferences(0, 1, 0);
            serverSocket.bind(new InetSocketAddress(444),1000000);

            //should be equal to port number in AliensAppActivity.java
            LOGGER.info("Server started. Waiting for a client.");
            LOGGER.info("Logger Name: " + LOGGER.getName());

            System.out.println("Press enter to continue...");
            new java.util.Scanner(System.in).nextLine();

            lastMessageTime = System.currentTimeMillis();
            while (true) {
                try {
                    // Check if there is an update from existing Aliens
                    Iterator<Map.Entry<String, Alien>> it = aliens.entrySet().iterator();
                    while (it.hasNext())
                    {
                        Alien currentAlien = it.next().getValue();
                        if (currentAlien.socket.isClosed() || !currentAlien.socket.isConnected())
                            continue;

                        long currentTime = System.currentTimeMillis();

                        InputStreamReader inputStreamReader = new InputStreamReader(currentAlien.socket.getInputStream());
                        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                        if (bufferedReader.ready())
                        {
                            String message = bufferedReader.readLine();
                            AddEvent(CLIENT_MESSAGE, message, currentTime, currentAlien);
                            ProcessMessage(message, currentTime, currentAlien);
                            lastMessageTime = currentTime;
                        }
                        else if (currentAlien.lastUpdateTime != 0){
                            // if the alien last update was before MAX_ALLOWED_INACTIVITY min ago - drop it
                            long timePassedSinceLastUpdate = currentTime - currentAlien.lastUpdateTime;
                            if (timePassedSinceLastUpdate > MAX_ALLOWED_INACTIVITY) {
                                LOGGER.info("Removed due to inactivity: " + currentAlien.ID);
                                it.remove();
                            }
                        }
                    }

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastMessageTime > MAX_TIME_WO_UPDATES)
                    {
                        lastMessageTime = currentTime;
                        LOGGER.info("currentTime: " + currentTime + ", lastMessageTime: " + lastMessageTime);
                        AddEvent(SELF_UPDATE, null, currentTime, null);
                        CheckAllCollisions(currentTime);
                    }

                    // Process single client connection
                    // TODO - move it to a thread

                    serverSocket.setSoTimeout(50); // sets timeout for accept to wait for in msecs. acccept will throw on TO end
                    try {
                        Socket clientSocket = serverSocket.accept(); // throws if no connection yet
                        Alien alienToProcess = new Alien();
                        alienToProcess.socket = clientSocket;

                        InetAddress remoteAddress = alienToProcess.socket.getInetAddress();
                        alienToProcess.IP = remoteAddress.getHostAddress();
                        aliens.put(alienToProcess.IP, alienToProcess);
                        LOGGER.info("Client connected from: " + alienToProcess.IP);

                    } catch (SocketTimeoutException e) {
                        //LOGGER.info("No new client yet");
                    }

                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }

    private static void TestServer(String clipDirectory) {
        try {
            normalMode = false;

            String filename = "./Testing.res";
            PrintWriter writer = new PrintWriter(filename, "UTF-8");

            File directory = new File(clipDirectory);
            //get all the files from a directory
            File[] fList = directory.listFiles();
            for (File file : fList) {
                if (file.isFile()) {
                    System.out.println("\n\n\nRunning on clip: " + file.getName());
                    writer.println("\nRunning on clip: " + file.getName());
                    LOGGER.log(Level.INFO, "\n\n\nRunning on clip: " + file.getName());
                    TestClip(file.getPath(), writer);
                }
            }
            writer.close();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }


   private static void ConvertClipToMatlab(String clipDirectory)
    {
        try {
            normalMode = false;

            String delims = "[.]";

            File directory = new File(clipDirectory);
            //get all the files from a directory
            File[] fList = directory.listFiles();
            for (File file : fList){
                if (file.isFile()) {
                    String fileNameFtomClipDirectory = file.getName();
                    String[] tokens = fileNameFtomClipDirectory.split(delims);
                    if (tokens[1].equals("clip")) {
                            System.out.println("\n\n\nConverting clip: " + file.getName());
                            LOGGER.log(Level.INFO, "\n\n\nConverting clip: " + file.getName());
                            ConvertClip(file.getPath());
                        }
                    }
            }


        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }

    private static void TestClip(String clipName, PrintWriter writer)
    {
        try {
            aliens.clear();
            warningIssued = null;

            File file = new File(clipName);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                ProcessTestEvent(line, writer);
            }
            fileReader.close();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }


    private static void ConvertClip(String clipName)
    {
        try {
            aliens.clear();
            warningIssued = null;


            File file = new File(clipName);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            //  change file extension to "file.matlab"

            String delims = "[.]";
            String[] tokens = clipName.split(delims);
            String dest = tokens[0].concat(".").concat("matlab");
            File file1 = new File(dest);

            String headFileName = tokens[0].concat("-Head").concat(".").concat("matlab");
            File headFile = new File(headFileName);


            PrintWriter writer = new PrintWriter(file1, "UTF-8");
            PrintWriter writer1 = new PrintWriter(headFileName, "UTF-8");


            String line;
            while ((line = bufferedReader.readLine()) != null) {
                ProcessClipLine(line, writer, writer1);
            }
            fileReader.close();
            writer.close();
            writer1.close();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }



    private static void HandleClipMissingWarning(long currentLineTime,PrintWriter writer)
    {
        if (null != warningIssued)
        {
            // test decided on warning, but no warning in the clip
            if (currentScenario.equals("FN")  &&  eventTime == currentLineTime)
            {
                writer.println("Correct warning (TP) " + warningIssued + " at " + currentLineTime);
                LOGGER.log(Level.SEVERE, "Correct warning (TP)" + warningIssued + " at " + currentLineTime);
            }
            else
            {
                writer.println("False warning (FP) " + warningIssued + " at " + currentLineTime);
                LOGGER.log(Level.SEVERE, "False warning (FP) " + warningIssued + " at " + currentLineTime);
            }
            warningIssued = null;
        }
    }

    private static void ProcessTestEvent(String event,PrintWriter writer)
    {
        try {
            String delims = "[ ]+";

            String[] tokens = event.split(delims);

            if (tokens[0].equals(SCENARIO))
            {
                currentScenario = tokens[1];
                eventTime = Long.parseLong(tokens[2]);
            }
            else if (tokens[0].equals(SELF_UPDATE))
            {
                long currentTime = Long.parseLong(tokens[1]);
                HandleClipMissingWarning(currentTime, writer);

                LOGGER.info("\n\nSelf Update at " + currentTime);
                CheckAllCollisions(currentTime);
            }
            else if (tokens[0].equals(CLIENT_MESSAGE))
            {
                HandleClipMissingWarning(Long.parseLong(tokens[2]), writer);

                Alien alienToProcess;
                if (!aliens.containsKey(tokens[1]))
                {
                    alienToProcess = new Alien();
                    alienToProcess.IP = tokens[1];
                    alienToProcess.ID = tokens[3];
                    aliens.put(tokens[1], alienToProcess);
                }
                else
                {
                    alienToProcess = aliens.get(tokens[1]);
                }
                //LOGGER.info("\n\nProcessing message from: " + alienToProcess.IP + " with payload: " + tokens[4]);
                ProcessMessage(tokens[4], Long.parseLong(tokens[2]), alienToProcess);
            }
            else if (tokens[0].equals(FRONT_END_WARNING)  ||  tokens[0].equals(COLLISION_WARNING))
            {
                if (null == warningIssued  ||  warningIssued.equals(""))
                {
                    if (currentScenario.equals("FP")  &&  eventTime == Long.parseLong(tokens[2]))
                    {
                        writer.println("Correct no warning (TN) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                        LOGGER.log(Level.SEVERE, "Correct no warning (TN) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                    }
                    else
                    {
                        writer.println("Missed warning (FN) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                        LOGGER.log(Level.SEVERE, "Missed warning (FN) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                    }
                }
                else if (!warningIssued.equals(tokens[0]))
                {
                    if (currentScenario.equals("FP")  &&  eventTime == Long.parseLong(tokens[2]))
                    {
                        writer.println("Wrong warning (FP) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                        LOGGER.log(Level.SEVERE, "Wrong warning (FP) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                    }
                    else
                    {
                        writer.println("Wrong warning (WP) " + tokens[0] + " instead of " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                        LOGGER.log(Level.SEVERE, "Wrong warning (WP) " + tokens[0] + " instead of " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                    }
                }
                else
                {
                    if (currentScenario.equals("FP") && eventTime == Long.parseLong(tokens[2]))
                    {
                        writer.println("Wrong warning (FP) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                        LOGGER.log(Level.SEVERE, "Wrong warning (FP) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                    }
                    else
                    {
                        writer.println("Correct warning (TP) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                        LOGGER.log(Level.SEVERE, "Correct warning (TP) " + tokens[0] + " at " + Long.parseLong(tokens[2]) + " aliens: " + tokens[4]);
                    }
                }

                warningIssued = null;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }


    private static void ProcessClipLine(String event,PrintWriter writer, PrintWriter writer1) {
        try {
            String delims = "[ ;]+";
            String[] tokens = event.split(delims);

            int length = tokens.length;
            int numberOfWordsInLine = 0;

            if (tokens[0].equals("SELF-UPDATE")){
                return;
            }

            if (tokens[0].equals("")) {
                return;
            }

            if (tokens[0].equals("Clip")){
                for (int i = 0; i < length; i++) {
                    writer1.print(tokens[i] + " ");
                    if (i == (length - 1)) {
                        writer1.println();
                    }
                }
                return;
            }


            if (tokens[0].equals("Alien:")) {
                for (int i = 0; i < length; i++) {
                    writer1.print(tokens[i] + " ");
                    if (i == (length - 1)) {
                        writer1.println();
                    }
                }
                return;
            }

            if (tokens[0].equals("Time:")){
                for (int i = 0; i < length; i++) {
                    writer1.print(tokens[i] + " ");
                    if (i == (length - 1)) {
                        writer1.println();
                    }
                }
                return;
            }

            if (tokens[0].equals("Scenario:")){
                for (int i = 0; i < length; i++) {
                    writer1.print(tokens[i] + " ");
                    if (i == (length - 1)) {
                        writer1.println();
                    }
                }
                return;
            }



            for (int i = 0; i < length; i++) {
                if (tokens[i].equals("CLIENT-MESSAGE")) {
                    writer.print("1 ");
                    numberOfWordsInLine++;
                } else if (tokens[i].equals("COLLISION-WARNING")) {
                    writer.print("2 ");
                    numberOfWordsInLine++;
                } else if (tokens[i].equals("true")) {
                    writer.print("1 ");
                    numberOfWordsInLine++;
                } else if (tokens[i].equals("false")) {
                    writer.print("0 ");
                    numberOfWordsInLine++;
                } else if (!tokens[i].contains("/") &&
                            !tokens[i].equals("upd") &&
                            !tokens[i].equals("rec") &&
                            !tokens[i].equals("TP"))
                {
                    writer.print(tokens[i] + " ");
                    numberOfWordsInLine++;
                }

                if (i == (length - 1)) {
                    if (numberOfWordsInLine < 9) {
                        int supplement = 9 - numberOfWordsInLine;
                        for (int k=0; k < supplement; k++){
                            writer.print("0.0 ");
                        }
                    }
                    writer.println();
                }
            }

        }    catch(Exception e){
                LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
                e.printStackTrace();
            }
        }


    private static void ProcessMessage(String message, long currentTime, Alien alienToProcess) {
        //LOGGER.info("\n\nProcessing message from: " + alienToProcess.IP + " - " + alienToProcess.ID + " at " + currentTime + " with payload: " + message);

        try {
            // Process message
            int len = message.length();
            ///////////////////////////////////////
            int startPos = 0;
            int endPos = message.indexOf(';');
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            String messageType = message.substring(startPos, endPos);
            if (messageType.equals("reg")) {
                // ID
                startPos = endPos + 1;
                if (startPos >= len) {
                    LOGGER.warning("Wrong message format");
                    return;
                }
                endPos = message.indexOf(';', startPos);
                if (-1 == endPos) {
                    LOGGER.warning("Wrong message format");
                    return;
                }

                alienToProcess.ID = message.substring(startPos, endPos);
                LOGGER.info("Received ID: " + alienToProcess.ID);

                AcknowledgeIfNeeded(alienToProcess, currentTime);
                return;

            }
            else if (messageType.equals("test")) {

                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! for PRX testing !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                /*message = PROXIMITY_WARNING;
                message += alienToProcess.IP + ".1" + ";" + 3866739.095702664 + ";" + 3757517.5232050065 + ";";
                message += alienToProcess.IP + ".2" + ";" + 3866729.095702664 + ";" + 3757510.5232050065 + ";";
                message += alienToProcess.IP + ".3" + ";" + 3866829.095702664 + ";" + 3757417.5232050065 + ";";*/

                int currentLevel = 1 + ++warningLevel % 2;
                message = DANGER_WARNING;
                message += Integer.toString(currentLevel) + ";" + "2" + ";" + alienToProcess.IP + ".1" + ";" + 3866739.095702664 + ";" + 3757517.5232050065 + ";";

                try {
                    PrintWriter out = new PrintWriter(alienToProcess.socket.getOutputStream(), true);
                    out.println(message); // Print the message on output stream.
                } catch (IOException e) {
                    LOGGER.warning("Failed to print DNG message");
                }
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! for PRX testing !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            }
            else if (messageType.equals("resetoffsets")) {
                if (!normalMode)
                    return;

                ResetOffsets(alienToProcess, currentTime);

                AcknowledgeIfNeeded(alienToProcess, currentTime);
                return;
            }
            else if (messageType.equals("rec")) {
                if (!normalMode)
                    return;

                // scenario type
                startPos = endPos + 1;
                if (startPos >= len) {
                    LOGGER.warning("Wrong message format");
                    return;
                }
                endPos = message.indexOf(';', startPos);
                if (-1 == endPos) {
                    LOGGER.warning("Wrong message format");
                    return;
                }
                String scenarioType = message.substring(startPos, endPos);
                LOGGER.info("Received recording request: " + scenarioType);

                RecordClip(alienToProcess, scenarioType, currentTime);
                return;
            }
            else if (messageType.equals("chkpnt")) {
                if (!normalMode)
                    return;

                double []currentOffsets = new double[2];

                ///////////////////////////////////////
                // offsetX
                startPos = endPos + 1;
                if (startPos >= len) {
                    LOGGER.warning("Wrong message format");
                    return;
                }
                endPos = message.indexOf(';', startPos);
                if (-1 == endPos) {
                    LOGGER.warning("Wrong message format");
                    return;
                }

                String field = message.substring(startPos, endPos);
                currentOffsets[0] = Double.parseDouble(field);


                ///////////////////////////////////////
                // locationY
                startPos = endPos + 1;
                if (startPos >= len) {
                    LOGGER.warning("Wrong message format");
                    return;
                }
                endPos = message.indexOf(';', startPos);
                if (-1 == endPos) {
                    LOGGER.warning("Wrong message format");
                    return;
                }

                field = message.substring(startPos, endPos);
                currentOffsets[1] = Double.parseDouble(field);

                UpdateOffsets(alienToProcess, currentOffsets, currentTime);

                AcknowledgeIfNeeded(alienToProcess, currentTime);
                return;
            }
            else if (messageType.equals("event")) {
                if (!normalMode)
                    return;

                ///////////////////////////////////////
                // event payload
                startPos = endPos + 1;
                if (startPos >= len) {
                    LOGGER.warning("Wrong message format");
                    return;
                }

                String field = message.substring(startPos, len - 1);
                LOGGER.info("Client event received from: " + alienToProcess.IP + ", at: " + currentTime + ", payload: " + field);

                AcknowledgeIfNeeded(alienToProcess, currentTime);
                return;
            }
            else if (!messageType.equals("upd")) {
                LOGGER.warning("Wrong message format - unknown type");
                return;
            }

            alienToProcess.prevVelocity = alienToProcess.velocity;
            if (!alienToProcess.direction)
                alienToProcess.prevVelocity = -alienToProcess.prevVelocity;


            ///////////////////////////////////////
            // locationX
            startPos = endPos + 1;
            if (startPos >= len) {
                LOGGER.warning("Wrong message format");
                return;
            }
            endPos = message.indexOf(';', startPos);
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            String field = message.substring(startPos, endPos);
            alienToProcess.XY[0] = Double.parseDouble(field);
            //LOGGER.info("Received locationX: " + alienToProcess.locationX);


            ///////////////////////////////////////
            // locationY
            startPos = endPos + 1;
            if (startPos >= len) {
                LOGGER.warning("Wrong message format");
                return;
            }
            endPos = message.indexOf(';', startPos);
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            field = message.substring(startPos, endPos);
            alienToProcess.XY[1] = Double.parseDouble(field);
            //LOGGER.info("Received locationY: " + alienToProcess.locationY);


            ///////////////////////////////////////
            // A
            startPos = endPos + 1;
            if (startPos >= len) {
                LOGGER.warning("Wrong message format");
                return;
            }
            endPos = message.indexOf(';', startPos);
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            field = message.substring(startPos, endPos);
            alienToProcess.A = Double.parseDouble(field);
            //LOGGER.info("Received A: " + alienToProcess.A);


            ///////////////////////////////////////
            // B
            startPos = endPos + 1;
            if (startPos >= len) {
                LOGGER.warning("Wrong message format");
                return;
            }
            endPos = message.indexOf(';', startPos);
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            field = message.substring(startPos, endPos);
            alienToProcess.B = Double.parseDouble(field);
            //LOGGER.info("Received B: " + alienToProcess.B);


            ///////////////////////////////////////
            // direction
            startPos = endPos + 1;
            if (startPos >= len) {
                LOGGER.warning("Wrong message format");
                return;
            }
            endPos = message.indexOf(';', startPos);
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            field = message.substring(startPos, endPos);
            alienToProcess.direction = Boolean.parseBoolean(field);
            //LOGGER.info("Received B: " + alienToProcess.B);


            ///////////////////////////////////////
            // velocity
            startPos = endPos + 1;
            if (startPos >= len) {
                LOGGER.warning("Wrong message format");
                return;
            }
            endPos = message.indexOf(';', startPos);
            if (-1 == endPos) {
                LOGGER.warning("Wrong message format");
                return;
            }

            field = message.substring(startPos, endPos);
            alienToProcess.velocity = Double.parseDouble(field);
            //LOGGER.info("Received velocity: " + alienToProcess.velocity);

            ///////////////////////////////////////
            // alien type
            startPos = endPos + 1;
            if (startPos >= len) {
                if (normalMode) // in testing mode could happen in old clips
                {
                    LOGGER.warning("Wrong message format");
                    return;
                }
            }
            else
            {
                endPos = message.indexOf(';', startPos);
                if (-1 == endPos) {
                    LOGGER.warning("Wrong message format");
                    return;
                }

                field = message.substring(startPos, endPos);
                int alienType = Integer.parseInt(field);
                alienToProcess.ownType = Alien.Type.values()[alienType];
            }

            alienToProcess.lastUpdateTime = currentTime;
            alienToProcess.lastUpdateMessageTime = currentTime;
            if (!alienToProcess.initialized) {
                // this will avoid issuing a warning at very beginning of alien life
                alienToProcess.lastWarningTime = currentTime;
            }
            alienToProcess.initialized = true;

            //AddEvent(ALIEN_UPDATED, " " + alienToProcess.ID + " " + alienToProcess.IP + " " + alienToProcess.XY[0] + " " + alienToProcess.XY[1] + " " + alienToProcess.A + " " + alienToProcess.B + " " + alienToProcess.direction + " " + alienToProcess.velocity, currentTime, alienToProcess);

            /* not closing here as we keep all connections alive - for demo only
            clientSocket.close();
             */

            final HashSet<IDsPair> testedPairs = new HashSet<>(); // used to avoid collision checks for aliens a and b already checked as b and a.
            CheckAlienCollisions(alienToProcess, currentTime, testedPairs);

            AcknowledgeIfNeeded(alienToProcess, currentTime);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
            e.printStackTrace();
        }
    }

    private static void CheckAllCollisions(long currentTime)
    {
        final HashSet<IDsPair> testedPairs = new HashSet<>(); // used to avoid collision checks for aliens a and b already checked as b and a.

        Iterator<Map.Entry<String, Alien>> it = aliens.entrySet().iterator();
        while (it.hasNext())
        {
            Alien currentAlien = it.next().getValue();
            if (!currentAlien.initialized)
                continue;

            if (normalMode) {
                if (currentAlien.socket.isClosed() || !currentAlien.socket.isConnected())
                    continue;

                if (currentTime - currentAlien.lastUpdateMessageTime > MAX_INACTIVITY_FOR_WARNING)
                    continue;
            }

            CheckAlienCollisions(currentAlien, currentTime, testedPairs);
            lastMessageTime = currentTime;
        }
    }

    private static void CheckAlienCollisions(Alien alienToProcess, long currentTime, HashSet<IDsPair> testedPairs)
    {
        LOGGER.info("CheckAlienCollisions for: " + alienToProcess.IP + " - " + alienToProcess.ID + " at: " + currentTime);

        // Front-end scenario check
        EmergencyBrakingFrontEndCollisionsCheck(alienToProcess, currentTime);

        // Since we just checked for braking of alienToProcess, here we can clear the followers - this should be ok, it will be updated soon below again.
        // this is easier than keeping track for inter alien connections
        alienToProcess.followers.clear();

        // collisions check

        String farAliensMessage = PROXIMITY_WARNING;

        // can't remove aliens here, since there is an outer loop over it in main()
        Iterator<Map.Entry<String, Alien>> it = aliens.entrySet().iterator();
        while (it.hasNext())
        {
            Alien currentAlien = it.next().getValue();
            if (!currentAlien.initialized)
                continue;

            if (currentAlien.ID.equals(alienToProcess.ID))
                continue; // no point of comparing with itself

            // ignore aliens which had not updated their data for too long - information about their dynamics could be wrong
            if (currentTime - currentAlien.lastUpdateMessageTime > MAX_INACTIVITY_FOR_WARNING)
                continue;

            IDsPair currentPair = new IDsPair(Long.parseLong(alienToProcess.ID), Long.parseLong(currentAlien.ID));
            boolean alreadyTested = testedPairs.contains(currentPair);

            // update currentAlien location - based on currentTime

            // first calculate distance, passed by the alien since last update - fix for TFS130
            long timeDelta = currentTime - currentAlien.lastUpdateTime;
            if (timeDelta > MAX_TIME_WO_UPDATES_FROM_ALIEN)
            {
                double distanceCoveredByCurrentAlien = currentAlien.velocity * timeDelta / 1000;
                if (distanceCoveredByCurrentAlien > 0.0) {
                    // now - the new coordinates
                    Geodesy.destination2dPoint(currentAlien.XY, distanceCoveredByCurrentAlien, currentAlien.A, currentAlien.direction, currentAlien.XY);
                    LOGGER.info("Updated alien with ID: " + currentAlien.ID + " " + currentAlien.XY[0] + ";" + currentAlien.XY[1]);

                    alreadyTested = false;
                }
                currentAlien.lastUpdateTime = currentTime;
            }

            if (alreadyTested)
                continue;

            testedPairs.add(currentPair);

            farAliensMessage += currentAlien.IP + ";" + currentAlien.XY[0] + ";" + currentAlien.XY[1] + ";";

            // main collisions calculation algorithm
            int collisionStatus = CalculateTwoAliensCollisions(alienToProcess, currentAlien, currentTime);
            boolean inDanger = collisionStatus != 0;

            // "warning scheme" - suppressions etc
            if (inDanger) {
                LOGGER.info("Found danger alien: " + currentAlien.ID + ", with collisionStatus: " + collisionStatus);

                boolean shouldIssue = true;
                long timeSinceLastWarning = currentTime - currentAlien.lastWarningTime;
                if (timeSinceLastWarning < TIMEOUT_BETWEEN_TWO_WARNINGS)
                {
                    if (collisionStatus <= currentAlien.lastDangerLevel)
                    {
                        shouldIssue = false;
                        LOGGER.info("Warning suppressed - not enough time passed since last one for " + currentAlien.ID);
                    }
                    else
                    {
                        if (timeSinceLastWarning < TIMEOUT_BETWEEN_TWO_WARNINGS_OF_DIFFERENT_LEVEL)
                        {
                            shouldIssue = false;
                            LOGGER.info("Warning suppressed - not enough time passed since last warning of lower level for " + currentAlien.ID);
                        }
                    }
                }

                if (shouldIssue) {
                    currentAlien.lastWarningTime = currentAlien.lastAckTime = currentTime;
                    String currentAlienMessage = DANGER_WARNING + collisionStatus + ";" + alienToProcess.ownType.ordinal() + ";" + alienToProcess.IP + ";" + alienToProcess.XY[0] + ";" + alienToProcess.XY[1] + ";";
                    IssueMessage(currentAlienMessage, currentAlien);

                    currentAlien.lastDangerLevel = collisionStatus;

                    warningIssued = COLLISION_WARNING; // needed in testing mode - we keep track of the last warning to compare with coming event in the clip
                    AddEvent(COLLISION_WARNING, alienToProcess.ID + ";" + currentAlien.ID, currentTime, currentAlien);
                }

                timeSinceLastWarning = currentTime - alienToProcess.lastWarningTime;
                if (timeSinceLastWarning < TIMEOUT_BETWEEN_TWO_WARNINGS)
                {
                    if (collisionStatus <= alienToProcess.lastDangerLevel)
                    {
                        shouldIssue = false;
                        LOGGER.info("Warning suppressed - not enough time passed since last one for " + alienToProcess.ID);
                    }
                    else
                    {
                        if (timeSinceLastWarning < TIMEOUT_BETWEEN_TWO_WARNINGS_OF_DIFFERENT_LEVEL)
                        {
                            shouldIssue = false;
                            LOGGER.info("Warning suppressed - not enough time passed since last warning of lower level for " + alienToProcess.ID);
                        }
                    }
                }

                if (shouldIssue) {
                    alienToProcess.lastWarningTime = alienToProcess.lastAckTime = currentTime;
                    String alienToProcessMessage = DANGER_WARNING + collisionStatus + ";" + currentAlien.ownType.ordinal() + ";" + currentAlien.IP + ";" + currentAlien.XY[0] + ";" + currentAlien.XY[1] + ";";
                    IssueMessage(alienToProcessMessage, alienToProcess);

                    alienToProcess.lastDangerLevel = collisionStatus;

                    warningIssued = COLLISION_WARNING; // needed in testing mode - we keep track of the last warning to compare with coming event in the clip
                    AddEvent(COLLISION_WARNING, alienToProcess.ID + ";" + currentAlien.ID, currentTime, alienToProcess);
                }
            }
        }

        if (currentTime - alienToProcess.lastNeighborsUpdateTime > MIN_TIME_TO_UPDATE_FAR_ALIENS  &&  !farAliensMessage.equals(PROXIMITY_WARNING))
        {
            alienToProcess.lastNeighborsUpdateTime = currentTime;
            IssueMessage(farAliensMessage, alienToProcess);
        }
    }

    private static void AcknowledgeIfNeeded(Alien alienToProcess, long currentTime)
    {
        if (currentTime - alienToProcess.lastAckTime < MAX_INACTIVITY_FOR_WARNING)
            return;

        alienToProcess.lastAckTime = currentTime;
        String alienToProcessMessage = ACK_MESSAGE + alienToProcess.IP + ";";
        IssueMessage(alienToProcessMessage, alienToProcess);
    }

    private static void IssueMessage(String message, Alien receiver)
    {
        if (!normalMode)
        {
            return;
        }

        try {
            PrintWriter out = new PrintWriter(receiver.socket.getOutputStream(), true);
            out.println(message); // Print the message on output stream.
        } catch (IOException e) {
            LOGGER.warning("Failed to issue message");
        }

    }

    private static void UpdateJoinRide(Alien alienToProcess, Alien currentAlien, long currentTime)
    {
        if (null != alienToProcess.joinRide  &&
            null != currentAlien.joinRide  &&
            alienToProcess.joinRide.hostVehicle.id == currentAlien.joinRide.hostVehicle.id)
        {
            alienToProcess.joinRide.lastOnTime = currentTime;
            currentAlien.joinRide.lastOnTime = currentTime;
        }
        else
        {
            Vehicle newVehicle = new Vehicle();
            alienToProcess.joinRide = new JoinRide(newVehicle, currentTime);
            currentAlien.joinRide = new JoinRide(newVehicle, currentTime);
        }
    }

    private static boolean IsStillInJoinRide(Alien alienToProcess, Alien currentAlien, long currentTime)
    {
        if (null == alienToProcess.joinRide  ||
                null == currentAlien.joinRide  ||
                alienToProcess.joinRide.hostVehicle.id != currentAlien.joinRide.hostVehicle.id)
        {
            return false;
        }

        if (currentTime - alienToProcess.joinRide.lastOnTime > MAX_TIME_TO_LEAVE_RIDE  ||
                currentTime - currentAlien.joinRide.lastOnTime > MAX_TIME_TO_LEAVE_RIDE)
        {
            alienToProcess.joinRide = null;
            currentAlien.joinRide = null;
            return false;
        }

        LOGGER.info("Current alien is still in the same vehicle as alienToProcess");
        return true;
    }

    // returns danger level:
    //      0 - no danger
    //      1 - low danger
    //      2 - high danger
    private static int CalculateTwoAliensCollisions(Alien alienToProcess, Alien currentAlien, long currentTime)
    {
        // check hypothesis
        IDsPair currentPair = new IDsPair(Long.parseLong(alienToProcess.ID), Long.parseLong(currentAlien.ID));
        CollisionHypothesis currentHypothesis = collisionsHypothesis.remove(currentPair);
        boolean hypothesisExisted = currentHypothesis != null;

        // Check distance between alienToProcess and current
        // TODO - replace distance check by assigning aliens to "cells"
        double distanceToAlienToProcess = Geodesy.distanceBetween2d(alienToProcess.XY, currentAlien.XY);
        if (MAX_DISTANCE_TO_CHECK_COLLISION < distanceToAlienToProcess)
            return 0;

        LOGGER.info("Found close alien: " + currentAlien.ID + " with distance: " + distanceToAlienToProcess);
        double velocitySum = currentAlien.velocity + alienToProcess.velocity;
        if (0 == velocitySum)
        {
            LOGGER.info("Aliens do not move - no danger");
            return 0;
        }
        double timeBetweenAliens = 1000 * distanceToAlienToProcess / velocitySum;
        if (timeBetweenAliens < MIN_COLLISION_TIME_TO_ISSUE_WARNING)
        {
            LOGGER.info("Aliens are too close - no point in warning");
            return 0;
        }

        // filter out aliens traveling with similar velocity/direction at close location - considering same vehicle
        if (MAX_DISTANCE_TO_CONSIDER_SAME_VEHICLE > distanceToAlienToProcess  &&
                MAX_SPEED_DELTA_TO_CONSIDER_SAME_VEHICLE > Math.abs(alienToProcess.velocity - currentAlien.velocity)  &&
                MAX_DIR_DELTA_TO_CONSIDER_SAME_VEHICLE > Math.abs(alienToProcess.A - currentAlien.A))
        {
            LOGGER.info("Current alien is in the same vehicle as alienToProcess");

            UpdateJoinRide(alienToProcess, currentAlien, currentTime);
            return 0;
        }

        if (IsStillInJoinRide(alienToProcess, currentAlien, currentTime))
            return 0;

        boolean followers = false;
        if (alienToProcess.velocity > MIN_DANGER_VELOCITY)
        {
            if (currentAlien.velocity > MIN_DANGER_VELOCITY)
            {
                // both aliens are on the move

                // check if move in same direction and update data structure - to be used in emergency braking
                if (Math.abs(alienToProcess.A - currentAlien.A) < MAX_ANGLE_DELTA_TO_ASSUME_SAME_DIR)
                {
                    LOGGER.info("Close directions");

                    double currentAlien_DistanceTo_AlienToProcessPath = Geodesy.distancePointToLine2d(alienToProcess.A, alienToProcess.B, currentAlien.XY);
                    double alienToProcess_DistanceTo_CurrentAlienPath = Geodesy.distancePointToLine2d(currentAlien.A, currentAlien.B, alienToProcess.XY);
                    if (currentAlien_DistanceTo_AlienToProcessPath < MAX_DISTANCE_BETWEEN_PARALLEL_PATHS  &&
                            alienToProcess_DistanceTo_CurrentAlienPath < MAX_DISTANCE_BETWEEN_PARALLEL_PATHS) {

                        LOGGER.info("Close paths");

                        // check what is the order between the aliens
                        if ((currentAlien.XY[0] < alienToProcess.XY[0] && alienToProcess.direction) ||
                                (alienToProcess.XY[0] < currentAlien.XY[0] && !alienToProcess.direction)) {
                            LOGGER.info("Current alien " + currentAlien.ID + " follows alienToProcess " + alienToProcess.ID);

                            followers = true;

                            ListIterator<Follower> followersIterator = alienToProcess.followers.listIterator();
                            while (followersIterator.hasNext()) {
                                Follower follower = followersIterator.next();
                                if (follower.distance > distanceToAlienToProcess) {
                                    followersIterator.previous();
                                    break;
                                }
                            }
                            Follower newFollower = new Follower(distanceToAlienToProcess, currentAlien);
                            followersIterator.add(newFollower);

                        } else {
                            LOGGER.info("Alien to process " + alienToProcess.ID + " follows current alien " + currentAlien.ID);

                            followers = true;

                            ListIterator<Follower> followersIterator = currentAlien.followers.listIterator();
                            while (followersIterator.hasNext()) {
                                Follower follower = followersIterator.next();
                                if (follower.distance > distanceToAlienToProcess)
                                {
                                    followersIterator.previous();
                                    break;
                                }
                            }
                            Follower newFollower = new Follower(distanceToAlienToProcess, alienToProcess);
                            followersIterator.add(newFollower);
                        }
                    }
                }


                // check for collision
                double []intersection = new double[2];
                boolean found = Geodesy.intersection2d(alienToProcess.A, alienToProcess.B, currentAlien.A, currentAlien.B, intersection);
                if (!found)
                    return 0; // no intersection

                LOGGER.info("Found collision point: " + intersection[0] + ", " + intersection[1]);

                // check that aliens move towards the collision point
                if (intersection[0] < alienToProcess.XY[0]  &&  alienToProcess.direction)
                {
                    LOGGER.info("Alien to process moves away from the collision point - no danger");
                    return 0;
                }
                if (alienToProcess.XY[0] < intersection[0]  &&  !alienToProcess.direction)
                {
                    LOGGER.info("Alien to process moves away from the collision point - no danger");
                    return 0;
                }
                if (intersection[0] < currentAlien.XY[0]  &&  currentAlien.direction)
                {
                    LOGGER.info("Current alien moves away from the collision point - no danger");
                    return 0;
                }
                if (currentAlien.XY[0] < intersection[0]  &&  !currentAlien.direction)
                {
                    LOGGER.info("Current alien moves away from the collision point - no danger");
                    return 0;
                }

                // distances to collision point (we are under assumption of linear paths under MAX_DISTANCE_TO_CHECK_COLLISION restriction)
                double alienToProcessDistance = Geodesy.distanceBetween2d(alienToProcess.XY, intersection);
                double currentAlienDistance = Geodesy.distanceBetween2d(currentAlien.XY, intersection);
                LOGGER.info("distances to collision point: " + alienToProcessDistance + ";" + currentAlienDistance);

                // times to get to collision point
                double alienToProcessTime = 1000 * alienToProcessDistance / alienToProcess.velocity;
                double currentAlienTime = 1000 * currentAlienDistance / currentAlien.velocity;
                LOGGER.info("Times to collision point: " + alienToProcessTime + ";" + currentAlienTime);

                if (alienToProcessTime < MIN_COLLISION_TIME_TO_ISSUE_WARNING  ||  currentAlienTime < MIN_COLLISION_TIME_TO_ISSUE_WARNING)
                {
                    LOGGER.info("Aliens are too close - no point in warning");
                    return 0;
                }

                if (followers)
                {
                    // aliens are following one-another, we will ignore close followers with small speed delta - these are ME clients
                    if ((alienToProcess.velocity >= currentAlien.velocity && alienToProcess.velocity - currentAlien.velocity < MIN_DANGER_VELOCITY
                            || alienToProcess.velocity < currentAlien.velocity && currentAlien.velocity - alienToProcess.velocity < MIN_DANGER_VELOCITY)
                        /*&& (currentAlienTime < MAX_EMERGENCY_TIME_TO_COLLISION_POINT || alienToProcessTime < MAX_EMERGENCY_TIME_TO_COLLISION_POINT)*/) // TN-TwoFollowingBicycles2.clip
                    {
                        LOGGER.info("Two following close aliens - no danger");
                        return 0;
                    }
                }

                if (alienToProcessTime < MAX_TIME_TO_COLLISION_POINT  &&
                        Math.abs(alienToProcessTime - currentAlienTime) < MAX_TIME_DIFFERENCE_TO_GET_TO_COLLISION_POINT) {

                    if (currentAlienTime < MAX_EMERGENCY_TIME_TO_COLLISION_POINT || alienToProcessTime < MAX_EMERGENCY_TIME_TO_COLLISION_POINT)
                        return 2;

                    if (hypothesisExisted)
                    {
                        int hypothesisStatus = currentHypothesis.CheckStatus(alienToProcess, alienToProcessTime, currentAlien, currentAlienTime, distanceToAlienToProcess, currentTime);
                        if (hypothesisStatus == 0) // danger decreased
                        {
                            return 0;
                        }
                        else if (hypothesisStatus == 2) // danger increased
                        {
                            return 2;
                        }
                    }
                    else
                    {
                        //LOGGER.info("Warning suppressed - waiting for hypothesis support");
                        currentHypothesis = new CollisionHypothesis(alienToProcess, alienToProcessTime, currentAlien, currentAlienTime, distanceToAlienToProcess, currentTime);
                    }
                    collisionsHypothesis.put(currentPair, currentHypothesis);
                    return 1;
                }
            }
            else
            {
                // alienToProcess moves, currentAlien - does not (almost)
                if (alienToProcess.velocity - currentAlien.velocity < MIN_DANGER_VELOCITY)
                {
                    LOGGER.info("Low relative velocity - no danger");
                    return 0;
                }

                // check that aliens move towards the collision point
                if (currentAlien.XY[0] < alienToProcess.XY[0]  &&  alienToProcess.direction)
                {
                    LOGGER.info("Alien to process moves away from the current alien - no danger");
                    return 0;
                }
                if (alienToProcess.XY[0] < currentAlien.XY[0]  &&  !alienToProcess.direction)
                {
                    LOGGER.info("Alien to process moves away from the current alien - no danger");
                    return 0;
                }

                // check if currentAlien is on the move and his path is not parallel to alienToProcess path
                if (currentAlien.velocity > 0  &&
                        MAX_ANGLE_DELTA_TO_ASSUME_SAME_DIR > Math.abs(alienToProcess.A - currentAlien.A))
                {
                    LOGGER.info("Aliens are moving in parallel - no point in warning");
                    return 0;
                }

                double alienToProcessTime = 1000 * distanceToAlienToProcess / alienToProcess.velocity;
                if (alienToProcessTime < MIN_COLLISION_TIME_TO_ISSUE_WARNING)
                {
                    LOGGER.info("Aliens are too close - no point in warning");
                    return 0;
                }

                // calculate distance (x) between alienToProcess and currentAlien at distanceToAlienToProcess (d) from alienToProcess in alienToProcess's A (!)
                double []estimatedAlienToProcessXY = new double[2];
                Geodesy.destination2dPoint(alienToProcess.XY, distanceToAlienToProcess, alienToProcess.A, alienToProcess.direction, estimatedAlienToProcessXY);

                double estimatedDistanceBetweenAliens = Geodesy.distanceBetween2d(estimatedAlienToProcessXY, currentAlien.XY);
                LOGGER.info("Estimated distance between: " + estimatedDistanceBetweenAliens);

                if (estimatedDistanceBetweenAliens < MAX_DISTANCE_TO_DECLARE_COLLISION  && alienToProcessTime < MAX_TIME_TO_COLLISION_POINT) {

                    if (alienToProcessTime < MAX_EMERGENCY_TIME_TO_COLLISION_POINT)
                        return 2;

                    if (hypothesisExisted)
                    {
                        int hypothesisStatus = currentHypothesis.CheckStatus(alienToProcess, alienToProcessTime, currentAlien, 0, distanceToAlienToProcess, currentTime);
                        if (hypothesisStatus == 0) // danger decreased
                        {
                            return 0;
                        }
                        else if (hypothesisStatus == 2) // danger increased
                        {
                            return 2;
                        }
                    }
                    else
                    {
                        //LOGGER.info("Warning suppressed - waiting for hypothesis support");
                        currentHypothesis = new CollisionHypothesis(alienToProcess, alienToProcessTime, currentAlien, 0, distanceToAlienToProcess, currentTime);
                    }
                    collisionsHypothesis.put(currentPair, currentHypothesis);
                    return 1;
                }
            }
        }
        else
        {
            if (currentAlien.velocity > MIN_DANGER_VELOCITY)
            {
                // alienToProcess does not move (almost), currentAlien - does
                if (currentAlien.velocity - alienToProcess.velocity < MIN_DANGER_VELOCITY)
                {
                    LOGGER.info("Low relative velocity - no danger");
                    return 0;
                }

                // check that aliens move towards the collision point
                if (currentAlien.XY[0] < alienToProcess.XY[0]  &&  !currentAlien.direction)
                {
                    LOGGER.info("Current alien moves away from the alien to process - no danger");
                    return 0;
                }
                if (alienToProcess.XY[0] < currentAlien.XY[0]  &&  currentAlien.direction)
                {
                    LOGGER.info("Current alien moves away from the alien to process - no danger");
                    return 0;
                }

                // check if alienToProcess is on the move and his path is not parallel to currentAlien path
                if (alienToProcess.velocity > 0  &&
                        MAX_ANGLE_DELTA_TO_ASSUME_SAME_DIR > Math.abs(currentAlien.A - alienToProcess.A))
                {
                    LOGGER.info("Aliens are moving in parallel - no point in warning");
                    return 0;
                }

                double currentAlienTime = 1000 * distanceToAlienToProcess / currentAlien.velocity;
                if (currentAlienTime < MIN_COLLISION_TIME_TO_ISSUE_WARNING)
                {
                    LOGGER.info("Aliens are too close - no point in warning");
                    return 0;
                }

                // calculate distance (x) between alienToProcess and currentAlien at distanceToAlienToProcess (d) from currentAlien in currentAlien's A (!)
                double []estimatedCurrentAlienXY = new double[2];
                Geodesy.destination2dPoint(currentAlien.XY, distanceToAlienToProcess, currentAlien.A, currentAlien.direction, estimatedCurrentAlienXY);

                double estimatedDistanceBetweenAliens = Geodesy.distanceBetween2d(estimatedCurrentAlienXY, alienToProcess.XY);
                LOGGER.info("Estimated distance between: " + estimatedDistanceBetweenAliens);

                if (estimatedDistanceBetweenAliens < MAX_DISTANCE_TO_DECLARE_COLLISION && currentAlienTime < MAX_TIME_TO_COLLISION_POINT) {

                    if (currentAlienTime < MAX_EMERGENCY_TIME_TO_COLLISION_POINT)
                        return 2;

                    if (hypothesisExisted)
                    {
                        int hypothesisStatus = currentHypothesis.CheckStatus(alienToProcess, 0, currentAlien, currentAlienTime, distanceToAlienToProcess, currentTime);
                        if (hypothesisStatus == 0) // danger decreased
                        {
                            return 0;
                        }
                        else if (hypothesisStatus == 2) // danger increased
                        {
                            return 2;
                        }
                    }
                    else
                    {
                        //LOGGER.info("Warning suppressed - waiting for hypothesis support");
                        currentHypothesis = new CollisionHypothesis(alienToProcess, 0, currentAlien, currentAlienTime, distanceToAlienToProcess, currentTime);
                    }
                    collisionsHypothesis.put(currentPair, currentHypothesis);
                    return 1;
                }
            }
            else
            {
                // both aliens do not move - no warning is needed
                LOGGER.info("Low speeds, no danger");
            }
        }

        return 0;
    }

    private static void EmergencyBrakingFrontEndCollisionsCheck(Alien brakingAlien, long currentTime)
    {
        // TODO - replace (or complete) velocity check by work with gyro signal on host device

        // TODO - check if direction has changed since prev velocity
        // we don't check times here, as client usually will update at most once a sec
        if (brakingAlien.prevVelocity - brakingAlien.velocity < MIN_EMERGENCY_BRAKING_VELOCITY_DELTA)
            return;

        // TODO - probably need to go recursively on followers of followers

        ListIterator<Follower> followersIterator = brakingAlien.followers.listIterator();
        while (followersIterator.hasNext()) {
            Follower follower = followersIterator.next();
            Alien alienToWarn = follower.alien;

            String message = DANGER_WARNING + "2;" + brakingAlien.ownType.ordinal() + ";" + brakingAlien.IP + ";" + brakingAlien.XY[0] + ";" + brakingAlien.XY[1] + ";";
            IssueMessage(message, alienToWarn);
            alienToWarn.lastWarningTime = alienToWarn.lastAckTime = currentTime;
            alienToWarn.lastDangerLevel = 2;

            LOGGER.info("Emergency braking warning: " + brakingAlien.ID + " " + alienToWarn.ID);
            warningIssued = FRONT_END_WARNING; // needed in testing mode - we keep track of the last warning to compare with coming event in the clip
            AddEvent(FRONT_END_WARNING, brakingAlien.ID + ";" + alienToWarn.ID, currentTime, alienToWarn);
        }
    }

    private static void AddEvent(String type, String data, long currentTime, Alien ownerAlien)
    {
        if (!normalMode)
        {
            type = TESTING + type;
        }
        alienEvents.add(new AlienEvent(type, data, currentTime, ownerAlien));
        if (null != ownerAlien)
            LOGGER.info("Event added: " + type + " " + ownerAlien.IP + " " + currentTime + " " + ownerAlien.ID + " " + new Date(currentTime) + " " + data);
        else
            LOGGER.info("Event added: " + type + " " + currentTime + " " + new Date(currentTime));

        // remove old events
        boolean isCleanUpPossible = alienEventsLock.tryLock();
        if (!isCleanUpPossible)
            return;

        try
        {
            AlienEvent currentEvent = alienEvents.get(0);
            while (currentTime - currentEvent.eventTime > MAX_CLIP_DURATION)
            {
                alienEvents.remove(0);
                if (alienEvents.isEmpty())
                    break;

                currentEvent = alienEvents.get(0);
            }
        }
        finally
        {
            alienEventsLock.unlock();
        }
    }

    private static void RecordClip(Alien alienToProcess, String scenarioType, long currentTime)
    {
        class ThreadRecording implements Runnable {

            Alien alienToProcess;
            String scenarioType;
            long currentTime;
            int clipNumber;

            ThreadRecording(Alien alien, String type, long time, int number) { alienToProcess = alien; scenarioType = type; currentTime = time; clipNumber = number; }

            public void run() {

                try
                {
                    LOGGER.info("Start recording clip: " + clipNumber);

                    String filename = "./Aliens" + clipNumber + ".clip";
                    PrintWriter writer = new PrintWriter(filename, "UTF-8");
                    writer.println("Clip #" + clipNumber);
                    writer.println("Alien: " + alienToProcess.ID);
                    writer.println("Time: " + new Date(currentTime));
                    writer.println("Scenario: " + scenarioType);

                    Iterator<AlienEvent> eventsIterator = alienEvents.iterator();
                    while (eventsIterator.hasNext())
                    {
                        AlienEvent currentEvent = eventsIterator.next();
                        if (currentTime - currentEvent.eventTime > MAX_CLIP_DURATION)
                            continue;

                        if (currentTime < currentEvent.eventTime)
                            break;

                        if (null != currentEvent.eventAlien)
                            writer.println(currentEvent.eventType + " " + currentEvent.eventAlien.IP + " " + currentEvent.eventTime + " " + currentEvent.eventAlien.ID + " " + currentEvent.eventData);
                        else
                            writer.println(currentEvent.eventType + " " + currentEvent.eventTime);
                    }
                    writer.close();

                    LOGGER.info("Done recording clip: " + clipNumber);

                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "General error occur: " + e.toString(), e);
                    e.printStackTrace();
                }
            }
        }

        Thread t = new Thread(new ThreadRecording(alienToProcess, scenarioType, currentTime, ++clipCounter));
        t.start();
    }

    // TODO: should run periodically in order to get rid off olf offsets
    private static void UpdateOffsets(Alien alienToProcess, double []currentOffsets, long currentTime)
    {
        boolean isUpdatePossible = offsetsLock.tryLock();
        if (!isUpdatePossible)
        {
            LOGGER.info("Failed to update offsets from: " + alienToProcess.ID + " - failed to obtain lock at: " + currentTime + " " + new Date(currentTime));
            return;
        }

        double []averageOffsets = {0, 0};
        try
        {
            offsetsList.add(new Offsets(currentOffsets, currentTime));
            offsetsSum[0] = offsetsSum[0] + currentOffsets[0];
            offsetsSum[1] = offsetsSum[1] + currentOffsets[1];

            Offsets current = offsetsList.get(0);
            while (currentTime - current.time > MAX_OFFSET_LIFESPAN)
            {
                offsetsSum[0] = offsetsSum[0] - current.XY[0];
                offsetsSum[1] = offsetsSum[1] - current.XY[1];

                offsetsList.remove(0);
                LOGGER.info("Removed offsets from: " + current.time);

                if (offsetsList.isEmpty())
                    break;

                current = offsetsList.get(0);
            }

            if (offsetsList.size() > 0)
            {
                averageOffsets[0] = offsetsSum[0] / offsetsList.size();
                averageOffsets[1] = offsetsSum[1] / offsetsList.size();
            }

            LOGGER.info("Updated offsets from: " + alienToProcess.ID + " successfully, current values: " + averageOffsets[0] + ", " + averageOffsets[1]);
        }
        finally
        {
            offsetsLock.unlock();
        }

        BroadcastUpdatedOffsets(averageOffsets, currentTime);
    }

    private static void ResetOffsets(Alien alienToProcess, long currentTime)
    {
        boolean isUpdatePossible = offsetsLock.tryLock();
        if (!isUpdatePossible)
        {
            LOGGER.info("Failed to reset offsets from: " + alienToProcess.ID + " - failed to obtain lock at: " + currentTime + " " + new Date(currentTime));
            return;
        }

        try
        {
            offsetsList.clear();
            offsetsSum[0] = 0;
            offsetsSum[1] = 0;

            LOGGER.info("Reset offsets from: " + alienToProcess.ID + " successfully");
        }
        finally
        {
            offsetsLock.unlock();
        }

        double []averageOffsets = {0, 0};
        BroadcastUpdatedOffsets(averageOffsets, currentTime);
    }

    private static void BroadcastUpdatedOffsets(double []averageOffsets, long currentTime) {
        Iterator<Map.Entry<String, Alien>> it = aliens.entrySet().iterator();
        while (it.hasNext()) {
            Alien currentAlien = it.next().getValue();
            if (!currentAlien.initialized)
                continue;

            currentAlien.lastAckTime = currentTime;
            String alienToProcessMessage = OFFSETS_MESSAGE + averageOffsets[0] + ";" + averageOffsets[1] + ";";
            IssueMessage(alienToProcessMessage, currentAlien);
        }
    }
}