/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.shell;

import static com.android.shell.BugreportProgressService.EXTRA_BUGREPORT;
import static com.android.shell.BugreportProgressService.EXTRA_ORIGINAL_INTENT;
import static com.android.shell.BugreportProgressService.INTENT_BUGREPORT_FINISHED;
import static com.android.shell.BugreportProgressService.getFileExtra;
import static com.android.shell.BugreportProgressService.dumpIntent;
import static com.android.shell.BugreportProgressService.EXTRA_SCREENSHOT;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.FileUtils;
import android.text.format.DateUtils;
import android.util.Log;

/**
 * Receiver that handles finished bugreports, usually by attaching them to an
 * {@link Intent#ACTION_SEND_MULTIPLE}.
 */
public class BugreportReceiver extends BroadcastReceiver {
    private static final String TAG = "BugreportReceiver";

    /**
     * Always keep the newest 8 bugreport files; 4 reports and 4 screenshots are
     * roughly 17MB of disk space.
     */
    private static final int MIN_KEEP_COUNT = 8;

    /**
     * Always keep bugreports taken in the last week.
     */
    private static final long MIN_KEEP_AGE = DateUtils.WEEK_IN_MILLIS;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive(): " + dumpIntent(intent));
        final File bugreportFile = getFileExtra(intent, EXTRA_BUGREPORT);
        final File screenshotFile = getFileExtra(intent, EXTRA_SCREENSHOT);

        copyFileToLogs(context, bugreportFile, screenshotFile);
        // Clean up older bugreports in background
        cleanupOldFiles(this, intent, INTENT_BUGREPORT_FINISHED, MIN_KEEP_COUNT, MIN_KEEP_AGE);

        // Delegate intent handling to service.
        Intent serviceIntent = new Intent(context, BugreportProgressService.class);
        serviceIntent.putExtra(EXTRA_ORIGINAL_INTENT, intent);
        context.startService(serviceIntent);
    }

    static void cleanupOldFiles(BroadcastReceiver br, Intent intent, String expectedAction,
            final int minCount, final long minAge) {
        if (!expectedAction.equals(intent.getAction())) {
            return;
        }
        final File bugreportFile = getFileExtra(intent, EXTRA_BUGREPORT);
        if (bugreportFile == null || !bugreportFile.exists()) {
            Log.e(TAG, "Not deleting old files because file " + bugreportFile + " doesn't exist");
            return;
        }
        final PendingResult result = br.goAsync();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    FileUtils.deleteOlderFiles(bugreportFile.getParentFile(), minCount, minAge);
                } catch (RuntimeException e) {
                    Log.e(TAG, "RuntimeException deleting old files", e);
                }
                result.finish();
                return null;
            }
        }.execute();
    }

	private static void copyFileToLogs(Context context, File report, File screenshot) {
        String LOGS_PATH = "/sdcard/Logs/";
        try {
            if(!new File(LOGS_PATH).exists())
                return;
            if(report.isFile() && screenshot.isFile()){
                copyFileUsingFileStreams(report, new File(LOGS_PATH + report.getName()));
                copyFileUsingFileStreams(screenshot, new File(LOGS_PATH + screenshot.getName()));
            }
        } catch (Exception e) {
        }
        context.sendBroadcast(new Intent("com.asus.debugger.intent_action.DUMPSTATE_DONE"));
    }

    private static void copyFileUsingFileStreams(File source, File dest) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0)
                output.write(buf, 0, bytesRead);
        } finally {
            input.close();
            output.close();
        }
    }
}
