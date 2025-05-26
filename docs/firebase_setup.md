# Firebase Setup for Homeostasis App

This document provides step-by-step instructions for setting up Firebase for the Homeostasis app.

## Prerequisites

- Google account
- Android Studio installed
- Homeostasis project cloned to your local machine

## 1. Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project"
3. Enter "Homeostasis" as the project name
4. Choose whether to enable Google Analytics (recommended)
5. Accept the terms and click "Create project"
6. Wait for the project to be created, then click "Continue"

## 2. Register Your Android App

1. In the Firebase Console, click the Android icon (</>) to add an Android app
2. Enter the package name: `com.homeostasis.app`
3. Enter "Homeostasis" as the app nickname
4. Enter your app's SHA-1 signing certificate (optional for development, required for production)
   - For development, you can get this by running the following command in your project directory:
     ```
     ./gradlew signingReport
     ```
5. Click "Register app"

## 3. Download and Add Configuration File

1. Download the `google-services.json` file
2. Move the file to the `app/` directory of your Homeostasis project
   - Replace the existing `google-services.json.sample` file
3. Make sure the file is properly added to your project

## 4. Set Up Firebase Authentication

1. In the Firebase Console, go to "Authentication"
2. Click "Get started"
3. Enable the "Email/Password" sign-in method
4. (Optional) Enable other sign-in methods like Google, Facebook, etc.

## 5. Set Up Firebase Firestore

1. In the Firebase Console, go to "Firestore Database"
2. Click "Create database"
3. Choose "Start in production mode" or "Start in test mode" (for development)
   - For development, "test mode" is easier as it allows all reads and writes
   - For production, you'll need to set up security rules
4. Choose a location for your database (pick the one closest to your users)
5. Click "Enable"

## 6. Set Up Firebase Storage

1. In the Firebase Console, go to "Storage"
2. Click "Get started"
3. Choose "Start in production mode" or "Start in test mode" (for development)
4. Choose a location for your storage (same as Firestore)
5. Click "Done"

## 7. Configure Security Rules

### Firestore Security Rules

For development, you can use the following rules:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

For production, you should implement more restrictive rules. Here's a starting point:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read and update their own data
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Tasks can be read by all authenticated users, but only created/updated by their owners
    match /tasks/{taskId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                            resource.data.createdBy == request.auth.uid;
    }
    
    // Task history can be read by all authenticated users, but only created by their owners
    match /taskHistory/{historyId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                            resource.data.userId == request.auth.uid;
    }
    
    // Categories can be read by all authenticated users, but only created/updated by their owners
    match /categories/{categoryId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                            resource.data.createdBy == request.auth.uid;
    }
    
    // Shopping lists can be read by all authenticated users, but only created/updated by their owners
    match /shoppingLists/{listId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                            resource.data.createdBy == request.auth.uid;
    }
    
    // Shopping items can be read by all authenticated users, but only created/updated by their owners
    match /shoppingItems/{itemId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                            resource.data.createdBy == request.auth.uid;
    }
    
    // Shopping list items can be read by all authenticated users, but only created/updated by their owners
    match /shoppingListItems/{listItemId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                            resource.data.addedBy == request.auth.uid;
    }
    
    // Settings can be read and updated by all authenticated users
    match /settings/{settingId} {
      allow read, write: if request.auth != null;
    }
    
    // User settings can be read and updated by their owners
    match /userSettings/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Reset history can be read by all authenticated users, but only created by their owners
    match /resetHistory/{resetId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if false; // No updates or deletes allowed
    }
  }
}
```

### Storage Security Rules

For development, you can use the following rules:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

For production, you should implement more restrictive rules. Here's a starting point:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // User profile images
    match /profileImages/{userId}/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Task attachments
    match /taskAttachments/{taskId}/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

## 8. Initialize Firebase in Your App

The Homeostasis app is already set up to initialize Firebase in the `HomeostasisApplication.kt` file. Make sure the following dependencies are in your `app/build.gradle` file:

```gradle
// Firebase
implementation platform('com.google.firebase:firebase-bom:32.7.3')
implementation 'com.google.firebase:firebase-auth-ktx'
implementation 'com.google.firebase:firebase-firestore-ktx'
implementation 'com.google.firebase:firebase-storage-ktx'
implementation 'com.google.firebase:firebase-messaging-ktx'
```

And make sure the Google Services plugin is applied in your `app/build.gradle` file:

```gradle
plugins {
    id 'com.google.gms.google-services'
}
```

## 9. Test Firebase Integration

1. Build and run the app
2. Try to register a new user
3. Check the Firebase Console to see if the user was created
4. Try to create a task
5. Check the Firebase Console to see if the task was created

## Troubleshooting

- If you encounter issues with Firebase Authentication, make sure you've enabled the Email/Password sign-in method
- If you encounter issues with Firestore or Storage, make sure you've set up the security rules correctly
- If you encounter issues with the `google-services.json` file, make sure it's in the correct location and has the correct package name
- If you encounter issues with dependencies, make sure you're using the correct versions

## Next Steps

- Set up Firebase Cloud Messaging for push notifications
- Set up Firebase Analytics for tracking user behavior
- Set up Firebase Crashlytics for crash reporting
- Set up Firebase Performance Monitoring for performance tracking