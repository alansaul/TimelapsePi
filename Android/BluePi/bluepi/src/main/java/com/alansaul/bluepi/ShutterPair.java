package com.alansaul.bluepi;

/**
 * Created by alansaul on 01/04/2015.
 */
public class ShutterPair {
    public String shutterstr;
    public float shutterspeed;

    public ShutterPair(String shutterStr, float shutterspeed){
        this.shutterstr = shutterStr;
        this.shutterspeed = shutterspeed;
    }

    public String key(){
        return this.shutterstr;
    }

    public float value(){
        return this.shutterspeed;
    }
}
