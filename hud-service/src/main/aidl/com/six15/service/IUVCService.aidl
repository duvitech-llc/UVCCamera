package com.six15.service;

import com.six15.service.IUVCServiceCallback;
import android.hardware.usb.UsbDevice;
import android.view.Surface;

/**
	<select						select UVC camera
		<connect				open device and start streaming
		disconnect>				stop streaming and close device
	release>					release camera
*/
interface IUVCService {
	int select(in UsbDevice device, IUVCServiceCallback callback);
	void release(int serviceId);
	boolean isSelected(int serviceId);
	void releaseAll();
	void resize(int serviceId, int width, int height);
	void connect(int serviceId);
	void disconnect(int serviceId);
	boolean isConnected(int serviceId);
	void addSurface(int serviceId, int id_surface, in Surface surface, boolean isRecordable);
	void removeSurface(int serviceId, int id_surface);
	boolean isRecording(int serviceId);
	void startRecording(int serviceId);
	void stopRecording(int serviceId);
	void captureStillImage(int serviceId, String path);
}