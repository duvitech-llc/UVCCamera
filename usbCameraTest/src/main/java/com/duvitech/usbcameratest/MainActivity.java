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

package com.duvitech.usbcameratest;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.duvitech.common.BaseActivity;
import com.duvitech.usb.CameraDialog;
import com.duvitech.usb.IButtonCallback;
import com.duvitech.usb.IFrameCallback;
import com.duvitech.usb.IStatusCallback;
import com.duvitech.usb.Size;
import com.duvitech.usb.USBMonitor;
import com.duvitech.usb.USBMonitor.OnDeviceConnectListener;
import com.duvitech.usb.USBMonitor.UsbControlBlock;
import com.duvitech.usb.UVCCamera;
import com.duvitech.usbcameratest.R;
import com.duvitech.widget.SimpleUVCCameraTextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent, IFrameCallback {
	private static final String TAG = MainActivity.class.getSimpleName();
	String[] perms = {"android.permission.CAMERA","android.permission.WRITE_EXTERNAL_STORAGE"};
	int permsRequestCode = 200;

	private boolean cameraAccepted = false;
	private boolean fileAccepted = false;

	private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
	private UVCCamera mUVCCamera;
	private SimpleUVCCameraTextureView mUVCCameraView;
	// for open&start / stop&close camera preview
	private ToggleButton mCameraButton;
	private Surface mPreviewSurface;
	private static  Activity myActivity;

	private SoundPool mSoundPool;
	private ImageButton camera_still = null;
	private ImageView zoom_plus = null;
	private ImageView zoom_minus = null;
	private Spinner camResSpinner = null;
	private ResolutionListAdapter mResAdapter = null;
	private int mSoundId;
	private int mSoundZoomId;
	DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

	private ToggleButton mCameraTestButton;

	private ArrayList<CameraResolution> supportedResolutions = new ArrayList<>();
	private int currentResolutionIndex = -1;

	private boolean bFirstFPSSinceStart = true;
	private static int zoom = 0;


	long startTime = 0;

	//runs without a timer by reposting this handler at the end of the runnable
	Handler timerHandler = new Handler();
	Runnable timerRunnable = new Runnable() {

		@Override
		public void run() {
			long millis = System.currentTimeMillis() - startTime;
			int seconds = (int) (millis / 1000);
			int minutes = seconds / 60;
			seconds = seconds % 60;

			if(mUVCCameraView != null) {
				Log.d("CAM-TEST", "Changing Resolution");
				currentResolutionIndex++;

				if (currentResolutionIndex >= supportedResolutions.size()) {
					Log.e(TAG, "Position is out of bounds setting to 0");
					currentResolutionIndex = 0; // back to default
				}

				camResSpinner.setSelection(currentResolutionIndex);

				mUVCCamera.stopPreview();
				if (mPreviewSurface != null) {
					mPreviewSurface.release();
					mPreviewSurface = null;
				}

				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}


				try {
					mUVCCamera.setPreviewSize(supportedResolutions.get(currentResolutionIndex).getWidth(), supportedResolutions.get(currentResolutionIndex).getHeight(), UVCCamera.FRAME_FORMAT_MJPEG);
					Toast.makeText(myActivity, "" + supportedResolutions.get(currentResolutionIndex).getWidth() + " x " + supportedResolutions.get(currentResolutionIndex).getHeight(), Toast.LENGTH_SHORT).show();

				} catch (final IllegalArgumentException e) {
					// fallback to YUV mode
					try {
						Toast.makeText(myActivity, "Failed Requested Stream", Toast.LENGTH_SHORT).show();
						mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
					} catch (final IllegalArgumentException e1) {
						mUVCCamera.destroy();
						Log.e("ERROR", "Could Not set resolution");
						Toast.makeText(myActivity, "FATAL ERROR", Toast.LENGTH_SHORT).show();
						return;
					}
				}

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// set aspect ration
						mUVCCameraView.setAspectRatio(supportedResolutions.get(currentResolutionIndex).getWidth() / (float) supportedResolutions.get(currentResolutionIndex).getHeight());
					}
				});

				final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
				if (st != null) {
					mPreviewSurface = new Surface(st);
					mUVCCamera.setPreviewDisplay(mPreviewSurface);
					bFirstFPSSinceStart = true;
					mUVCCamera.startPreview();
					Log.d("getZoom", "" + zoom);
					timerHandler.postDelayed(this, 5000);
				}
			}else{
				Log.e("CAM-TEST", "UVC Camera View is NULL");
			}
		}
	};

	private AdapterView.OnItemSelectedListener mOnResolutionSelectListener = new AdapterView.OnItemSelectedListener() {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

			Log.i(TAG, "Selected: " + id + " Position: " + position +" Resolution: "+ mResAdapter.getItem(position).toString());
			currentResolutionIndex = position;
			if (mSoundPool != null)
				mSoundPool.play(mSoundZoomId, 0.2f, 0.2f, 0, 0, 1.0f);    // play shutter sound
			else
				Log.e("SOUND", "sound pool is empty");

			if(currentResolutionIndex>=supportedResolutions.size()){
				Log.e(TAG, "Position is out of bounds setting to 0");
				currentResolutionIndex = 0; // back to default
			}

			mUVCCamera.stopPreview();
			if (mPreviewSurface != null) {
				mPreviewSurface.release();
				mPreviewSurface = null;
			}

			try {
				Thread.sleep(1000);
			}catch(Exception e){}


			try {
				mUVCCamera.setPreviewSize(supportedResolutions.get(currentResolutionIndex).getWidth(), supportedResolutions.get(currentResolutionIndex).getHeight(), UVCCamera.FRAME_FORMAT_MJPEG);
				Toast.makeText(myActivity, "" +supportedResolutions.get(currentResolutionIndex).getWidth() + " x " + supportedResolutions.get(currentResolutionIndex).getHeight(), Toast.LENGTH_SHORT).show();

			} catch (final IllegalArgumentException e) {
				// fallback to YUV mode
				try {
					Toast.makeText(myActivity, "Failed Requested Stream", Toast.LENGTH_SHORT).show();
					mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
				} catch (final IllegalArgumentException e1) {
					mUVCCamera.destroy();
					Log.e("ERROR", "Could Not set resolution");
					Toast.makeText(myActivity, "FATAL ERROR", Toast.LENGTH_SHORT).show();
					return;
				}
			}

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// set aspect ration
					mUVCCameraView.setAspectRatio(supportedResolutions.get(currentResolutionIndex).getWidth()/(float)supportedResolutions.get(currentResolutionIndex).getHeight());
				}
			});

			final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
			if (st != null) {
				mPreviewSurface = new Surface(st);
				mUVCCamera.setPreviewDisplay(mPreviewSurface);
				bFirstFPSSinceStart = true;
				mUVCCamera.startPreview();
				Log.d("getZoom", "" + zoom);
			}

		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {

			Log.i(TAG, "Nothing Selected stays the same");
		}
	};


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		myActivity = this;
		bFirstFPSSinceStart = true;
		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN
						| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
						| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
						| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		mCameraButton = findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);

		mCameraTestButton = findViewById(R.id.btnTest);
		mCameraTestButton.setEnabled(false);
		mCameraTestButton.setOnClickListener(mOnTestClickListener);

		loadShutterSound(this);
		camResSpinner = findViewById(R.id.spinner_resolutions);
		camResSpinner.setOnItemSelectedListener(mOnResolutionSelectListener);
		camResSpinner.setPrompt("Select Camera Resolution:");
		camera_still = findViewById(R.id.camera_stillcapture);
		camera_still.setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View v) {

				if(mUVCCamera != null) {
					if (mSoundPool != null)
						mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);    // play shutter sound
					else
						Log.e("SOUND", "sound pool is empty");

					final Bitmap bmp = mUVCCameraView.getBitmap();
					Calendar cal = Calendar.getInstance();
					String image_name = dateFormat.format(cal.getTimeInMillis());
					String root = Environment.getExternalStorageDirectory().toString();
					File myDir = new File(root);
					myDir.mkdirs();
					String fname = "cap-" + image_name + ".jpg";
					File file = new File(myDir, fname);
					if (file.exists()) file.delete();
					Log.i("LOAD", root + "/" + fname);
					try {
						FileOutputStream out = new FileOutputStream(file);
						bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
						out.flush();
						out.close();
					} catch (Exception e) {
						e.printStackTrace();
					}

				}else{
					Toast.makeText(myActivity, "Camera OFF", Toast.LENGTH_SHORT).show();
				}

			}
		});

		zoom_plus = findViewById(R.id.zoom_plus);
		zoom_plus.setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View v) {
				if(mUVCCamera != null) {
					if(currentResolutionIndex != -1 && supportedResolutions.get(currentResolutionIndex).getMaxZoom() >0) {
						if(mSoundPool != null)
							mSoundPool.play(mSoundZoomId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
						else
							Log.e("SOUND", "sound pool is empty");
						zoom++;
						if (zoom > supportedResolutions.get(currentResolutionIndex).getMaxZoom())
							zoom = supportedResolutions.get(currentResolutionIndex).getMaxZoom();
						mUVCCamera.setZoom(zoom);
						v.setEnabled(false);
						try {
							Thread.sleep(250);
						}catch(Exception e){}
						v.setEnabled(true);
					}else{
						Toast.makeText(myActivity, "No Zoom Support", Toast.LENGTH_SHORT).show();
					}
				}else{
					Toast.makeText(myActivity, "Camera OFF", Toast.LENGTH_SHORT).show();
				}
			}
		});

		zoom_minus = findViewById(R.id.zoom_minus);
		zoom_minus.setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View v) {
				if(mUVCCamera != null) {
					if(currentResolutionIndex != -1 && supportedResolutions.get(currentResolutionIndex).getMaxZoom() >0) {
						if (mSoundPool != null)
							mSoundPool.play(mSoundZoomId, 0.2f, 0.2f, 0, 0, 1.0f);    // play shutter sound
						else
							Log.e("SOUND", "sound pool is empty");
						zoom--;
						if (zoom < 0)
							zoom = 0;
						mUVCCamera.setZoom(zoom);
						v.setEnabled(false);
						try {
							Thread.sleep(250);
						}catch(Exception e){}
						v.setEnabled(true);
					}else{
						Toast.makeText(myActivity, "No Zoom Support", Toast.LENGTH_SHORT).show();
					}
				}else{
					Toast.makeText(myActivity, "Camera OFF", Toast.LENGTH_SHORT).show();
				}
			}
		});


		mUVCCameraView = (SimpleUVCCameraTextureView)findViewById(R.id.UVCCameraTextureView1);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

		if(!fileAccepted || !cameraAccepted) {
			ActivityCompat.requestPermissions(this, perms, permsRequestCode);
		}


	}

	@Override
	protected void onStart() {
		super.onStart();
		mUSBMonitor.register();
		synchronized (mSync) {
			if (mUVCCamera != null) {
				bFirstFPSSinceStart = true;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// set aspect ration
						mUVCCameraView.setAspectRatio(supportedResolutions.get(currentResolutionIndex).getWidth()/(float)supportedResolutions.get(currentResolutionIndex).getHeight());
					}
				});

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

		if (mSoundPool != null) {
			try {
				mSoundPool.release();
			} catch (final Exception e) {
			}
			mSoundPool = null;
		}

		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		Log.d(TAG,"Back Button Pressed");
		new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle("Closing HUD Camera Demo")
				.setMessage("Press Back to exit or Cancel to return to app")
				.setOnKeyListener(new DialogInterface.OnKeyListener() {
					@Override
					public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
						switch (keyCode) {
							case KeyEvent.KEYCODE_BACK:
								Log.d(TAG, "Finish called");
								dialog.dismiss();
								((MainActivity)myActivity).onSuperBackPressed();
								return true;
							default:
								return false;
						}
					}
				})
				.setPositiveButton("Exit", new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
						((MainActivity)myActivity).onSuperBackPressed();
					}

				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void onSuperBackPressed() {
		releaseCamera();
		finish();
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			List<ActivityManager.AppTask> tasks = am.getAppTasks();
			if (tasks != null && tasks.size() > 0) {
				tasks.get(0).setExcludeFromRecents(true);
			}
		}
		android.os.Process.killProcess(android.os.Process.myPid());
		super.onBackPressed();
	}

	private final OnClickListener mOnTestClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			synchronized (mSync) {
				if(((ToggleButton)view).isChecked()) {
					startCameraTest();
				}else{
					stopCameraTest();
				}
			}
		}
	};

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
			((ToggleButton)view).setChecked(false);
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mSoundPool = new SoundPool.Builder()
					.setMaxStreams(2)
					.build();
		}else {
			mSoundPool = new SoundPool(2, streamType, 0);
		}
		mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
		mSoundZoomId = mSoundPool.load(context, R.raw.zoom_click, 1);
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
					supportedResolutions.clear();
					for(Size z:camera.getSupportedSizeList()){
						CameraResolution cr = null;
						if( camera.checkSupportFlag(0x20200 & (0x1 << 9))) {
							int maxZoom = -1;
							switch(z.width){
								case 320:
									maxZoom = 6;
									break;
								case 640:
									maxZoom = 6;
									break;
								case 1280:
									maxZoom = 1;
									break;
								case 1920:
									maxZoom = 1;
									break;
								default:
									maxZoom = -1;
									break;
							}
							cr = new CameraResolution(z.width, z.height, maxZoom);
						}else{
							cr = new CameraResolution(z.width, z.height, -1);
						}


						Log.e("SIZE", "Res; " + cr.getWidth() + " x " + cr.getHeight() + " ZoomEnabled: " + (cr.getMaxZoom()>-1?"true":"false"));
						supportedResolutions.add(cr);
					}
					if(supportedResolutions.size()>0) {
						//set to first resolution (default)
						currentResolutionIndex = 0;
						mResAdapter = new ResolutionListAdapter(myActivity, supportedResolutions);


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

					if (mPreviewSurface != null) {
						mPreviewSurface.release();
						mPreviewSurface = null;
					}
					try {
						camera.setPreviewSize(supportedResolutions.get(currentResolutionIndex).getWidth(), supportedResolutions.get(currentResolutionIndex).getHeight(), UVCCamera.FRAME_FORMAT_MJPEG);

						Toast.makeText(myActivity, "" +supportedResolutions.get(currentResolutionIndex).getWidth() + " x " + supportedResolutions.get(currentResolutionIndex).getHeight(), Toast.LENGTH_SHORT).show();

					} catch (final IllegalArgumentException e) {
						// fallback to YUV mode
						try {
							Toast.makeText(myActivity, "Failed Requested Stream", Toast.LENGTH_SHORT).show();
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
						bFirstFPSSinceStart = true;
						camera.startPreview();
						Log.d("getZoom", "" + zoom);
					}
					synchronized (mSync) {
						mUVCCamera = camera;
						// mUVCCamera.setFrameCallback((IFrameCallback) myActivity, PixelFormat.RGB_565);
					}
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							// Set Spinner adapter
							camResSpinner.setAdapter(mResAdapter);
							mCameraButton.setChecked(true);
							mCameraTestButton.setEnabled(true);
						}
					});
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

	private synchronized void startCameraTest(){
		Log.d("CAM-TEST", "Start Camera Test");
		startTime = System.currentTimeMillis();
		timerHandler.postDelayed(timerRunnable, 1000);
		mCameraButton.setChecked(true);
	}

	private synchronized void stopCameraTest(){
		Log.d("CAM-TEST", "Stop Camera Test");
		timerHandler.removeCallbacks(timerRunnable);
		mCameraButton.setChecked(false);

	}

	private synchronized void releaseCamera() {
		synchronized (mSync) {
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

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					stopCameraTest();
					mCameraTestButton.setEnabled(false);
					mCameraButton.setChecked(false);
					camResSpinner.setAdapter(null);
				}
			});
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
		currFrame = System.currentTimeMillis();
		if(prevFrame == 0)
			prevFrame = currFrame;
		else {
			frameTime = frameTime + (currFrame - prevFrame);
            prevFrame = currFrame;
		}
		if(countFrames>240) {
		    double fps = 1000.0/(frameTime/countFrames);
            frameTime = 0;
			countFrames = 0;
			if(bFirstFPSSinceStart){
				bFirstFPSSinceStart = false;
			}else {
				Log.d("FPS", "" + (int) fps);
				// tv_fps.setText(String.format("FPS: %s", (int) fps));
			}

		}
	}

	@Override
	public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults){

		switch(permsRequestCode){

			case 200:

				cameraAccepted = grantResults[0]== PackageManager.PERMISSION_GRANTED;
				fileAccepted = grantResults[1]== PackageManager.PERMISSION_GRANTED;

				break;

		}

	}

	private static final class ResolutionListAdapter extends BaseAdapter {

		private final LayoutInflater mInflater;
		private final List<CameraResolution> mList;

		public ResolutionListAdapter(final Context context, final ArrayList<CameraResolution>list) {
			mInflater = LayoutInflater.from(context);
			mList = list != null ? list : new ArrayList<CameraResolution>();
		}

		@Override
		public int getCount() {
			return mList.size();
		}

		@Override
		public CameraResolution getItem(final int position) {
			if ((position >= 0) && (position < mList.size()))
				return mList.get(position);
			else
				return null;
		}

		@Override
		public long getItemId(final int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView, final ViewGroup parent) {
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.listitem_device, parent, false);
			}
			if (convertView instanceof CheckedTextView) {
				final CameraResolution cam_res = getItem(position);
				((CheckedTextView)convertView).setText(
						String.format("%d x %d", cam_res.getWidth(), cam_res.getHeight()));

			}
			return convertView;
		}
	}


}
