package com.six15.service;

import com.six15.service.IUVCServiceOnFrameAvailable;
import android.view.Surface;

interface IUVCSlaveService {
	boolean isSelected(int serviceID);
	boolean isConnected(int serviceID);
	void addSurface(int serviceID, int id_surface, in Surface surface, boolean isRecordable, IUVCServiceOnFrameAvailable callback);
	void removeSurface(int serviceID, int id_surface);
}