# Android System Service Denial of Service PoC

An Android security proof of concept exploring a crafted Binder/Parcel request to a system service and repeated invocation from a separate process, tested on android phone simulator.

The intended outcome is disruption of `system_server` on a susceptible Android build.

## Research scope

The project brings together three areas of Android security research:

- **IPC input handling:** examining how a system service handles a deliberately constructed request.
- **Process lifecycle:** exploring whether a separate process continues running after the initiating activity or system services are interrupted.
- **Availability impact:** examining repeated attempts and their potential effect on system recovery.

## Source overview

| Component | Role |
| --- | --- |
| [`MainActivity.java`](app/src/main/java/com/example/csapp/MainActivity.java) | Connects the activity's button handler to the background runner and initial request |
| [`Main.java`](app/src/main/java/com/example/csapp/Main.java) | Constructs and submits the system service request, with command output and exit status logging |
| [`loopFunc.java`](app/src/main/java/com/example/csapp/loopFunc.java) | Starts a separate process and contains the repeated-invocation logic |
| [`activity_main.xml`](app/src/main/res/layout/activity_main.xml) | Defines the demonstration interface |
| [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) | Declares the application and launcher activity |

The implementation's entry method is `Main.restart()`. The background routine currently uses an **unbounded loop**.


### Recorded project configuration

| Setting | Value |
| --- | --- |
| Language | Java |
| Application ID | `com.example.csapp` |
| Minimum SDK | 31 |
| Compile / target SDK | 33 / 33 |
| Android Gradle Plugin | 8.1.0 |
| Gradle wrapper | 8.0 |


