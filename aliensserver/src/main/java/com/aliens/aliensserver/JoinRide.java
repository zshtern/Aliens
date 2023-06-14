package com.aliens.aliensserver;


public class JoinRide {

    JoinRide(Vehicle vehicle, long joinTime) { hostVehicle = vehicle; lastOnTime = joinTime; }

    Vehicle hostVehicle = null;
    long lastOnTime = 0;
}
