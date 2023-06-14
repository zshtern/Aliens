package com.aliens.aliensserver;


public class Vehicle
{
    Vehicle() { id = ++vacantId; }

    private static long vacantId = 0;

    public long id;
}
