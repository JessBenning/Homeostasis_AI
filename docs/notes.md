Based on the AndroidManifest.xml, I can confirm that:

1. The application class is `.HomeostasisApplication`
2. The main activity is `.ui.MainActivity`
3. The MainActivity is set as the launcher activity with the MAIN intent filter and LAUNCHER category

When creating a run/debug configuration in Android Studio, you would:

1. Select the "app" module
2. The default activity to launch would be "MainActivity"
3. The launch options would typically be set to "Default Activity"

Let me provide this information to the user.

When creating a run or debug configuration in Android Studio for the Homeostasis app, you should:

1. **Select the "app" module** - This is the standard module for Android applications and contains your entire app code.

2. **Launch options**:
   - The default activity to launch is `com.homeostasis.app.ui.MainActivity`
   - You can select "Default Activity" as the launch option

3. **Configuration details from AndroidManifest.xml**:
   - Application class: `com.homeostasis.app.HomeostasisApplication`
   - Main activity: `com.homeostasis.app.ui.MainActivity` (configured as the LAUNCHER activity)
   - Package name: `com.homeostasis.app`

4. **Deployment target**:
   - You can select either a physical device or an emulator
   - The app requires minimum SDK 26 (Android 8.0 Oreo) as specified in the build.gradle file

The app is properly set up with Firebase initialization in the Application class and authentication handling in the MainActivity and AuthFragment. When you run the app, it should check if a user is signed in and navigate to the authentication screen if not.