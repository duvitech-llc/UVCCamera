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

package com.serenegiant.common;

import android.app.Service;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.serenegiant.utils.HandlerThreadHandler;

public abstract class BaseService extends Service {
	private static boolean DEBUG = false;	// FIXME
	private static final String TAG = BaseService.class.getSimpleName();

	private final Handler mUIHandler = new Handler(Looper.getMainLooper());
	private final Thread mUiThread = mUIHandler.getLooper().getThread();
	private Handler mWorkerHandler;
	private long mWorkerThreadID = -1;

	@Override
	public void onCreate() {
		super.onCreate();
		// Create Worker Thread
		if (mWorkerHandler == null) {
			mWorkerHandler = HandlerThreadHandler.createHandler(TAG);
			mWorkerThreadID = mWorkerHandler.getLooper().getThread().getId();
		}
	}

	@Override
	public synchronized void onDestroy() {
		// Destroy Worker Thread
		if (mWorkerHandler != null) {
			try {
				mWorkerHandler.getLooper().quit();
			} catch (final Exception e) {
				//
			}
			mWorkerHandler = null;
		}
		super.onDestroy();
	}

//================================================================================
	/**
	 * Run ON ui
	 * @param task
	 * @param duration
	 */
	public final void runOnUiThread(final Runnable task, final long duration) {
		if (task == null) return;
		mUIHandler.removeCallbacks(task);
		if ((duration > 0) || Thread.currentThread() != mUiThread) {
			mUIHandler.postDelayed(task, duration);
		} else {
			try {
				task.run();
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
		}
	}

	/**
	 * Remove UI Task
	 * @param task
	 */
	public final void removeFromUiThread(final Runnable task) {
		if (task == null) return;
		mUIHandler.removeCallbacks(task);
	}

	/**
	 * Queue Runnable
	 * @param task
	 * @param delayMillis
	 */
	protected final synchronized void queueEvent(final Runnable task, final long delayMillis) {
		if ((task == null) || (mWorkerHandler == null)) return;
		try {
			mWorkerHandler.removeCallbacks(task);
			if (delayMillis > 0) {
				mWorkerHandler.postDelayed(task, delayMillis);
			} else if (mWorkerThreadID == Thread.currentThread().getId()) {
				task.run();
			} else {
				mWorkerHandler.post(task);
			}
		} catch (final Exception e) {
			// ignore
		}
	}

	/**
	 * Cancel Runnable
	 * @param task
	 */
	protected final synchronized void removeEvent(final Runnable task) {
		if (task == null) return;
		try {
			mWorkerHandler.removeCallbacks(task);
		} catch (final Exception e) {
			// ignore
		}
	}
}
