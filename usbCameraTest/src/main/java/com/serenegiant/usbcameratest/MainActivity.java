/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.SimpleUVCCameraTextureView;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Date;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent, IFrameCallback {

	private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
	private UVCCamera mUVCCamera;
	private SimpleUVCCameraTextureView mUVCCameraView;
	// for open&start / stop&close camera preview
	private ToggleButton mCameraButton;
	private Surface mPreviewSurface;
	private static  Activity myActivity;
	private TextView tv_fps = null;
	private SoundPool mSoundPool;
	private ImageButton camera_still = null;
	private int mSoundId;
	private static int zoom = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		myActivity = this;

		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN
						| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
						| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
						| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		mCameraButton = findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);

		tv_fps = findViewById(R.id.tv_fps);
		camera_still = findViewById(R.id.camera_stillcapture);
		camera_still.setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View v) {

				if(mSoundPool != null)
					mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
				else
					Log.e("SOUND", "sound pool is empty");
				mUVCCamera.setZoom(200);
				//mUVCCamera.getUsbControlBlock().getConnection().controlTransfer()
			}
		});

		mUVCCameraView = (SimpleUVCCameraTextureView)findViewById(R.id.UVCCameraTextureView1);
		mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

	}

	@Override
	protected void onStart() {
		super.onStart();
		mUSBMonitor.register();
		synchronized (mSync) {
			loadShutterSound(this);
			if (mUVCCamera != null) {
				mUVCCamera.startPreview();
			}
		}
	}

	@Override
	protected void onStop() {
		synchronized (mSync) {
			if (mUVCCamera != null) {
				mUVCCamera.stopPreview();
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.unregister();
			}
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		synchronized (mSync) {
			releaseCamera();
			if (mToast != null) {
				mToast.cancel();
				mToast = null;
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.destroy();
				mUSBMonitor = null;
			}
		}
		mUVCCameraView = null;
		mCameraButton = null;
		super.onDestroy();
	}

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			synchronized (mSync) {
				if (mUVCCamera == null) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					releaseCamera();
				}
			}
		}
	};

	private void loadShutterSound(final Context context) {
		Log.d("ShutterSound", "loadShutterSound:");
		// get system stream type using refrection
		int streamType;
		try {
			final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
			final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
			streamType = sseField.getInt(null);
		} catch (final Exception e) {
			streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
		}
		if (mSoundPool != null) {
			try {
				mSoundPool.release();
			} catch (final Exception e) {
			}
			mSoundPool = null;
		}
		// load sutter sound from resource
		mSoundPool = new SoundPool(2, streamType, 0);
		mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
	}

	private Toast mToast;

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
			// Log.d("onAttached", device.getManufacturerName());
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			releaseCamera();
			queueEvent(new Runnable() {
				@Override
				public void run() {
					final UVCCamera camera = new UVCCamera();
					camera.open(ctrlBlock);

					camera.updateCameraParams();
					for(Size z:camera.getSupportedSizeList()){
						Log.e("SIZE", "Res; " + z.width + " x " + z.height);
					}

					camera.setStatusCallback(new IStatusCallback() {
						@Override
						public void onStatus(final int statusClass, final int event, final int selector,
											 final int statusAttribute, final ByteBuffer data) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final Toast toast = Toast.makeText(MainActivity.this, "onStatus(statusClass=" + statusClass
											+ "; " +
											"event=" + event + "; " +
											"selector=" + selector + "; " +
											"statusAttribute=" + statusAttribute + "; " +
											"data=...)", Toast.LENGTH_SHORT);
									synchronized (mSync) {
										if (mToast != null) {
											mToast.cancel();
										}
										toast.show();
										mToast = toast;
									}
								}
							});
						}
					});
					camera.setButtonCallback(new IButtonCallback() {
						@Override
						public void onButton(final int button, final int state) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final Toast toast = Toast.makeText(MainActivity.this, "onButton(button=" + button + "; " +
											"state=" + state + ")", Toast.LENGTH_SHORT);
									synchronized (mSync) {
										if (mToast != null) {
											mToast.cancel();
										}
										mToast = toast;
										toast.show();
									}
								}
							});
						}
					});
//					camera.setPreviewTexture(camera.getSurfaceTexture());
					if (mPreviewSurface != null) {
						mPreviewSurface.release();
						mPreviewSurface = null;
					}
					try {
						camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
					} catch (final IllegalArgumentException e) {
						// fallback to YUV mode
						try {
							camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
						} catch (final IllegalArgumentException e1) {
							camera.destroy();
							return;
						}
					}
					final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
					if (st != null) {
						mPreviewSurface = new Surface(st);
						camera.setPreviewDisplay(mPreviewSurface);
//						camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565/*UVCCamera.PIXEL_FORMAT_NV21*/);
						camera.startPreview();
						Log.d("getZoom", "" + zoom);
					}
					synchronized (mSync) {
						mUVCCamera = camera;
						mUVCCamera.setFrameCallback((IFrameCallback) myActivity, PixelFormat.RGB_565);
					}
				}
			}, 0);
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			// XXX you should check whether the coming device equal to camera device that currently using
			releaseCamera();
		}

		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
		}
	};

	private synchronized void releaseCamera() {
		synchronized (mSync) {
			if(mSoundPool!=null){
				mSoundPool.release();
				mSoundPool = null;
			}
			if (mUVCCamera != null) {
				try {
					mUVCCamera.setStatusCallback(null);
					mUVCCamera.setButtonCallback(null);
					mUVCCamera.close();
					mUVCCamera.destroy();
				} catch (final Exception e) {
					//
				}
				mUVCCamera = null;
			}
			if (mPreviewSurface != null) {
				mPreviewSurface.release();
				mPreviewSurface = null;
			}
		}
	}

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (canceled) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// FIXME
				}
			}, 0);
		}
	}

	private static long prevFrame = 0;
	private static long currFrame = 0;
	private static long frameTime = 0;
	private static long countFrames = 0;

	@Override
	public void onFrame(ByteBuffer frame) {
		countFrames++;
		currFrame = SystemClock.elapsedRealtime();
		if(prevFrame == 0)
			prevFrame = currFrame;
		else {
			frameTime = frameTime + (currFrame - prevFrame);
            prevFrame = currFrame;
		}
		if(countFrames>120) {
		    double fps = 1000.0/(frameTime/countFrames);
            frameTime = 0;
			countFrames = 0;
			Log.d("FPS", "" + fps);

			tv_fps.setText(String.format("FPS: %s", fps));

		}
	}
}
