package com.aliens.aliensserver;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class Alien {

    Alien() { XY = new double [2]; followers = new ArrayList<Follower>(); }

    String ID = "not initialized";
    String IP = "not initialized";
    double []XY;
    double A; // A in y = A * x + b
    double B;
    boolean direction = true; // true - object moves along X in positive direction
    double velocity = 0; // m/s
    double prevVelocity = 0; // m/s

    Socket socket = null;
    long lastUpdateMessageTime = 0; // msec - last update message received
    long lastUpdateTime = 0; // msec - last update of Alien information (could be done by server estimation)
    long lastWarningTime = 0; // msec - last warning issued for this alien
    long lastAckTime = 0; // msec
    int lastDangerLevel = 0;

    long lastNeighborsUpdateTime = 0;

    List<Follower> followers; // objects, moving in the same direction, ordered by distance from this

    JoinRide joinRide = null; // used to identify objects moving in same vehicle and suppress warnings

    // should be moved to Common lib (used by app too)
    enum Type { UNKNOWN, PEDESTRIAN, VEHICLE };
    Type ownType = Type.PEDESTRIAN;

    boolean initialized = false;
}
