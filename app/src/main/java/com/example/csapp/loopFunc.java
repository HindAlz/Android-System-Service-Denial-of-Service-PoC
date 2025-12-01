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

public class loopFunc {

    private static final String TAG = "BackRun";

    //start this from your main app it makes a new hidden process that runs the code below
    //this is kind of like starting a second copy of your app but without any ui
    public static void start(Context context) throws IOException {

        //get basic info about our app so the child process knows what app it belongs to
        //think of this like giving someone a passport so they know your identity
        Parcel parcel = Parcel.obtain();
        context.getApplicationInfo().writeToParcel(parcel, 0);

        //turn that info into a base64 string so we can send it through app_process
        String appInfoB64 = Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP);
        parcel.recycle();

        //create a command that starts a new process using the android system tool "app_process"
        //app_process lets us run java code outside the normal app system
        ProcessBuilder pb = new ProcessBuilder(
                "/system/bin/app_process", //system tool that runs java classes directly
                "/",                       //system path it runs under
                loopFunc.class.getName(),  //tells it which class to run (this file)
                appInfoB64                 //gives it our app info so it can rebuild context
        );

        //set the classpath so the child process knows where our apk is stored
        //classpath is basically "where to find the code"
        Map<String, String> env = pb.environment();
        env.put("CLASSPATH", context.getApplicationInfo().sourceDir);

        //finally start the new process it runs in the background
        pb.start();
    }

    //this is the code that runs inside the new background process
    //think of this like an invisible worker running on the device
    public static void main(String[] args) {
        try {

            //try to detach from the terminal so the process keeps running on its own
            try {
                Os.setsid();
            } catch (Throwable t) {
                //if it fails whatever the process still works
            }

            //if no app info was given we cannot continue
            if (args == null || args.length < 1) {
                return;
            }

            //wait until the android core service "activity manager" is fully running
            //this is needed after boot when the system is not fully ready yet
            try {
                Class<?> smClz = Class.forName("android.os.ServiceManager");
                Method getService = smClz.getMethod("getService", String.class);

                //check the old value so we know when it changes
                Object oldAm = getService.invoke(null, "activity");

                //keep checking until the service responds correctly
                while (true) {
                    Object am = getService.invoke(null, "activity");
                    //if the service changed from its old state then it is ready
                    if (am != null && am != oldAm) break;
                    Log.v(TAG, "waiting for am");
                    Thread.sleep(2000);
                }
            } catch (Throwable t) {
                //ignore errors this is optional
            }

            //now decode the base64 string back into a real applicationinfo object
            //this lets us rebuild a fake context inside the child process
            ApplicationInfo appInfo;
            try {
                byte[] data = Base64.decode(args[0], Base64.DEFAULT);
                Parcel p = Parcel.obtain();
                p.unmarshall(data, 0, data.length);
                p.setDataPosition(0);
                appInfo = ApplicationInfo.CREATOR.createFromParcel(p);
                p.recycle();
            } catch (Throwable t) {
                return; //if decoding fails we cannot run anything
            }

            //decide if we want context or not here we always want it
            final boolean NEED_CONTEXT = true;

            Context context = null;

            if (NEED_CONTEXT) {
                try {
                    //we build a tiny fake context that works enough for basic tasks
                    //not a full android app context but good enough for background code
                    context = buildMinimalContext(appInfo);
                } catch (Throwable t) {
                    //ignore errors the code might still run without context
                }
            }

            //do the background task many times 
            //this is basically a loop that waits then does work
	    int i=0;
            while (true) {

                //sleep for a while so it doesn't spam the system
                Thread.sleep(10000);

                Log.v(TAG, "run " + (i + 1));

                try {
                    performBackgroundTask(context, i + 1);
                } catch (Throwable t) {
                    Log.e(TAG, "run fail " + (i + 1), t);
                }

                Log.v(TAG, "done run " + (i + 1));

                //first iterations have an extra sleep
                if (i < 1) {
                    try {
                        Log.v(TAG, "sleep a bit");
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "sleep broke", e);
                    }
                }
		i++;
            }

        } catch (Throwable outer) {
            //ignore top level crash so process dies quietly
        }
    }

    private static Context buildMinimalContext(ApplicationInfo appInfo) throws Exception {
        Log.v(TAG, "making tiny context");

        //a looper is like a message inbox for the thread
        //android needs this for context to work
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        //get the internal android object "activitythread"
        //this is the heart of every android app process
        Class<?> atClz = Class.forName("android.app.ActivityThread");
        Method systemMain = atClz.getDeclaredMethod("systemMain");
        Object activityThread = systemMain.invoke(null); // create one

        //try to get compatibility settings for the app
        Class<?> ciClz = Class.forName("android.content.res.CompatibilityInfo");
        Object compatInfo;
        try {
            Field defaultCompat = ciClz.getField("DEFAULT_COMPATIBILITY_INFO");
            compatInfo = defaultCompat.get(null);
        } catch (NoSuchFieldException nsf) {
            compatInfo = null; // not all versions have this
        }

        //next we load the apk into the child process
        //this gives the process access to resources and code
        Object loadedApk = null;
        Exception last = null;

        try {
            //some android versions use this signature
            Method m = atClz.getDeclaredMethod("getPackageInfoNoCheck", ApplicationInfo.class, ciClz);
            m.setAccessible(true);
            loadedApk = m.invoke(activityThread, appInfo, compatInfo);
        } catch (Exception e1) {
            last = e1;
            try {
                //older versions use a simpler one
                Method m = atClz.getDeclaredMethod("getPackageInfoNoCheck", ApplicationInfo.class);
                m.setAccessible(true);
                loadedApk = m.invoke(activityThread, appInfo);
            } catch (Exception e2) {
                last = e2;
                throw last;
            }
        }

        //check if we actually got a loadedapk (android internal object)
        Class<?> loadedApkClz = Class.forName("android.app.LoadedApk");
        if (!loadedApkClz.isInstance(loadedApk)) {
            throw new IllegalStateException("not loadedapk " + loadedApk);
        }

        //now create a working context object using activitythread + loadedapk
        //this is what lets the child process use resources or system services
        Class<?> ctxImplClz = Class.forName("android.app.ContextImpl");
        Method createAppContext =
                ctxImplClz.getDeclaredMethod("createAppContext", atClz, loadedApkClz);
        createAppContext.setAccessible(true);

        Context ctx = (Context) createAppContext.invoke(null, activityThread, loadedApk);
        return ctx;
    }

    private static void performBackgroundTask(Context context, int runNumber) throws Exception {
        //call your normal main work method from this hidden worker process
        Main.restart();
    }
}
