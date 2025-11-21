package com.example.csapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Looper;
import android.os.Parcel;
import android.system.Os;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class RebootBackgroundRunner {

    private static final String TAG = "BackRun";

    /**
     * Launch from your normal app process. Spawns an app_process that runs this class's main().
     */
    public static void start(Context context) throws IOException {
        // Serialize ApplicationInfo so the child can reconstruct minimal Context
        Parcel parcel = Parcel.obtain();
        context.getApplicationInfo().writeToParcel(parcel, 0);
        String appInfoB64 = Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP);
        parcel.recycle();

        ProcessBuilder pb = new ProcessBuilder(
                "/system/bin/app_process",
                "/",
                RebootBackgroundRunner.class.getName(),
                appInfoB64
        );

        // Set env on the builder, then start
        Map<String, String> env = pb.environment();
        env.put("CLASSPATH", context.getApplicationInfo().sourceDir);
        pb.start();
    }

    /**
     * Entry point when started via /system/bin/app_process.
     * Builds a minimal Context (optional) and runs the background task twice.
     */
    public static void main(String[] args) {
        try {
            try {
                Os.setsid(); // best-effort detach
            } catch (Throwable t) {
                Log.w(TAG, "setsid failed (non-critical)", t);
            }

            if (args == null || args.length < 1) {
                Log.e(TAG, "No ApplicationInfo payload provided");
                return;
            }

            // --- Wait for ActivityManager (useful after reboot/service restart) ---
            try {
                Class<?> smClz = Class.forName("android.os.ServiceManager");
                Method getService = smClz.getMethod("getService", String.class);
                Object oldAm = getService.invoke(null, "activity");
                while (true) {
                    Object am = getService.invoke(null, "activity");
                    if (am != null && am != oldAm) break;
                    Log.v(TAG, "Waiting for ActivityManager...");
                    Thread.sleep(2000);
                }
                Log.v(TAG, "ActivityManager ready");
            } catch (Throwable t) {
                Log.e(TAG, "AM wait block failed", t);
            }

            // --- Decode ApplicationInfo ---
            ApplicationInfo appInfo;
            try {
                byte[] data = Base64.decode(args[0], Base64.DEFAULT);
                Parcel p = Parcel.obtain();
                p.unmarshall(data, 0, data.length);
                p.setDataPosition(0);
                appInfo = ApplicationInfo.CREATOR.createFromParcel(p);
                p.recycle();
                Log.v(TAG, "ApplicationInfo decoded: " + appInfo.packageName + "@" + appInfo.sourceDir);
            } catch (Throwable t) {
                Log.e(TAG, "Decoding ApplicationInfo failed", t);
                return;
            }

            // Flip this while debugging: if false, we skip building Context and still run work.
            final boolean NEED_CONTEXT = true;

            Context context = null;
            if (NEED_CONTEXT) {
                try {
                    context = buildMinimalContext(appInfo);
                    Log.v(TAG, "Context created: " + context);
                } catch (Throwable t) {
                    Log.e(TAG, "Context build failed", t);
                    // If your work strictly needs Context, return here instead of continuing.
                    // return;
                }
            }

            // ---- Run work twice ----
            for (int i = 0; i < 20; i++) {
                Thread.sleep(10000);
                Log.v(TAG, "Run iteration " + (i + 1) + " of 2");
                try {
                    performBackgroundTask(context, i + 1); // replace with your real work
                } catch (Throwable t) {
                    Log.e(TAG, "Work iteration " + (i + 1) + " failed", t);
                }

                Log.v(TAG, "Iteration " + (i + 1) + " complete");

                // Add delay between iterations (5 seconds example)
                if (i < 1) { // skip after last iteration
                    try {
                        Log.v(TAG, "Sleeping 5 seconds before next iteration...");
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Sleep interrupted", e);
                    }
                }
            }

            Log.v(TAG, "All iterations finished. Exiting...");
            // If you need to keep handlers/messages alive, call Looper.loop() instead of exiting.
            // Looper.loop();
        } catch (Throwable outer) {
            Log.e(TAG, "Fatal in main()", outer);
        }
    }

    private static Context buildMinimalContext(ApplicationInfo appInfo) throws Exception {
        Log.v(TAG, "Building minimal Context…");

        // Ensure we have a Looper (systemMain usually prepares one, but guard anyway)
        if (Looper.myLooper() == null) {
            Log.v(TAG, "Preparing Looper");
            Looper.prepare();
        }

        // ActivityThread.systemMain()
        Class<?> atClz = Class.forName("android.app.ActivityThread");
        Method systemMain = atClz.getDeclaredMethod("systemMain");
        Object activityThread = systemMain.invoke(null);
        Log.v(TAG, "ActivityThread.systemMain() returned: " + activityThread);

        // CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO (may vary across versions)
        Class<?> ciClz = Class.forName("android.content.res.CompatibilityInfo");
        Object compatInfo;
        try {
            Field defaultCompat = ciClz.getField("DEFAULT_COMPATIBILITY_INFO");
            compatInfo = defaultCompat.get(null);
        } catch (NoSuchFieldException nsf) {
            Log.w(TAG, "DEFAULT_COMPATIBILITY_INFO not found, using null", nsf);
            compatInfo = null;
        }

        // Try multiple getPackageInfoNoCheck signatures
        Object loadedApk = null;
        Exception last = null;
        try {
            Method m = atClz.getDeclaredMethod("getPackageInfoNoCheck", ApplicationInfo.class, ciClz);
            m.setAccessible(true);
            loadedApk = m.invoke(activityThread, appInfo, compatInfo);
            Log.v(TAG, "LoadedApk via (ApplicationInfo, CompatibilityInfo)");
        } catch (Exception e1) {
            last = e1;
            Log.w(TAG, "First getPackageInfoNoCheck signature failed", e1);
            try {
                Method m = atClz.getDeclaredMethod("getPackageInfoNoCheck", ApplicationInfo.class);
                m.setAccessible(true);
                loadedApk = m.invoke(activityThread, appInfo);
                Log.v(TAG, "LoadedApk via (ApplicationInfo)");
            } catch (Exception e2) {
                last = e2;
                Log.e(TAG, "All getPackageInfoNoCheck attempts failed", e2);
                throw last;
            }
        }

        Class<?> loadedApkClz = Class.forName("android.app.LoadedApk");
        if (!loadedApkClz.isInstance(loadedApk)) {
            throw new IllegalStateException("loadedApk is not a LoadedApk: " + loadedApk);
        }

        // ContextImpl.createAppContext(ActivityThread, LoadedApk)
        Class<?> ctxImplClz = Class.forName("android.app.ContextImpl");
        Method createAppContext = ctxImplClz.getDeclaredMethod("createAppContext", atClz, loadedApkClz);
        createAppContext.setAccessible(true);
        Context ctx = (Context) createAppContext.invoke(null, activityThread, loadedApk);
        Log.v(TAG, "createAppContext succeeded");

        return ctx;
    }

    /**
     * Replace with your real background logic.
     */
    private static void performBackgroundTask(Context context, int runNumber) throws IOException {
        Log.i(TAG, "performBackgroundTask: run #" + runNumber + " context=" + context);
        Main.crashSystemServer();
    }
}
