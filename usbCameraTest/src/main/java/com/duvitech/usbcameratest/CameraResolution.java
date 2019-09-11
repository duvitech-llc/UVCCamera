package com.duvitech.usbcameratest;

import android.os.Parcel;
import android.os.Parcelable;

public class CameraResolution implements Parcelable {
    private int width;
    private int height;
    private int maxZoom;

    protected CameraResolution(Parcel in) {
        width = in.readInt();
        height = in.readInt();
        maxZoom = in.readInt();
    }

    public static final Creator<CameraResolution> CREATOR = new Creator<CameraResolution>() {
        @Override
        public CameraResolution createFromParcel(Parcel in) {
            return new CameraResolution(in);
        }

        @Override
        public CameraResolution[] newArray(int size) {
            return new CameraResolution[size];
        }
    };

    public int getWidth(){
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxZoom(){
        return maxZoom;
    }

    public CameraResolution(int w, int h, int zm){
        width = w;
        height = h;
        maxZoom = zm;
    }

    @Override
    public String toString() {
        return String.format("%d x %d",width,height);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeInt(maxZoom);
    }
}
