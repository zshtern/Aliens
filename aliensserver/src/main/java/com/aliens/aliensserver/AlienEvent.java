package com.aliens.aliensserver;

public class AlienEvent
{
    public String eventType;
    public String eventData;
    public long eventTime;
    public Alien eventAlien;

    public AlienEvent(String type, String data, long time, Alien alien) {
        eventType = type;
        eventData = data;
        eventTime = time;
        eventAlien = alien;
    }
}
