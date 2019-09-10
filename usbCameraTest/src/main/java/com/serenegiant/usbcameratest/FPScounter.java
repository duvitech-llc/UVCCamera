package com.serenegiant.usbcameratest;

import android.util.Log;

public class FPScounter {

    private static int startTime;
    private static int endTime;
    private static int frameTimes = 0;
    private static short frames = 0;

    private static short fps = 0;

    private static boolean bStarted = false;
    /** Start counting the fps**/
    public final static void StartCounter()
    {
        //get the current time
        startTime = (int) System.currentTimeMillis();
        bStarted = true;
    }

    public final static short getFps()
    {
        if(!bStarted){
            StartCounter();
            return 0;
        }

        //get the current time
        endTime = (int) System.currentTimeMillis();
        //the difference between start and end times
        frameTimes = frameTimes + endTime - startTime;
        //count one frame
        ++frames;
        if(frameTimes >= 1000)
        {
            fps = frames;
            //post results at the console
            Log.i("FPS", String.format("Frametime: %d FPS %d",frameTimes, Long.toString(fps)));

            //reset time differences and number of counted frames
            frames = 0;
            frameTimes = 0;
        }

        return fps;

    }

    /**stop counting the fps and display it at the console*/
    public final static void StopAndPost()
    {
        bStarted = false;
        //get the current time
        endTime = (int) System.currentTimeMillis();
        //the difference between start and end times
        frameTimes = frameTimes + endTime - startTime;
        //count one frame
        ++frames;
        //if the difference is greater than 1 second (or 1000ms) post the results
        if(frameTimes >= 1000)
        {
            //post results at the console
            Log.i("FPS", Long.toString(frames));
            //reset time differences and number of counted frames
            frames = 0;
            frameTimes = 0;
        }
    }

}
