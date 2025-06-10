# User Profile Settings Feature Plan

This document outlines the plan for implementing the user profile settings feature in the Homeostasis app, allowing users to create and edit their profile name and profile picture.

## Requirements

*   Users should be able to edit their profile name.
*   Users should be able to select a profile picture from their device's gallery.
*   The profile picture should be saved locally and uploaded to Firebase Cloud Storage.
*   The profile picture must have a maximum size of 1MB.
*   The profile picture must have a minimum dimension of 250x250px.
*   The profile picture should be displayed in a circular format in the UI.
*   A profile settings page should be accessible from the user icon on the bottom navigation bar.

## Plan

1.  **Examine existing data models:** (Completed) The `User.kt` data class already contains `name` and `profileImageUrl` fields, which are suitable for storing the profile name and the URL of the profile picture in Firebase Cloud Storage. `UserSettings.kt` is not relevant for this feature.
2.  **Update data models:** (Not needed) No changes are required for the existing data models.
3.  **Implement Image Processing:**
    *   Create a new utility class (e.g., `ImageUtils.kt`) or add functions within the `ProfileSettingsViewModel` to handle image processing.
    *   This processing will include:
        *   Selecting an image from the device's gallery using an Activity Result API (e.g., `ActivityResultContracts.GetContent`).
        *   Decoding the selected image into a Bitmap.
        *   Resizing the image to meet the minimum dimension requirement of 250x250px while maintaining aspect ratio.
        *   Compressing the image (e.g., to JPEG format with a specific quality) to ensure it stays within the 1MB size limit. This might require iterative compression.
        *   Potentially implementing logic for cropping the image into a circle for display purposes (this can be done during display rather than as part of the saved image).
        *   Returning the processed image data, likely as a `ByteArray` or a processed `Uri`.
4.  **Implement Cloud Storage logic:**
    *   The existing `FirebaseStorageRepository.kt` has an `uploadBytes` function that can be used to upload the processed image data (`ByteArray`) to Firebase Cloud Storage.
    *   We will use a specific path in Cloud Storage for profile pictures, perhaps something like `profile_pictures/{userId}/profile.jpg`.
    *   The `uploadBytes` function returns the download URL of the uploaded image, which will be stored in the user's Firestore document.
5.  **Update Repositories:**
    *   Inject `FirebaseStorageRepository` into `UserRepository.kt`.
    *   Add a new suspend function to `UserRepository.kt`, for example `updateUserProfileWithPicture(userId: String, name: String, imageBytes: ByteArray?)`. This function will:
        *   If `imageBytes` is not null:
            *   Upload the `imageBytes` to Firebase Cloud Storage using `FirebaseStorageRepository.uploadBytes`, specifying the appropriate path and filename (e.g., `profile_pictures/$userId/profile.jpg`).
            *   Get the download URL from the upload result.
        *   Prepare a map of updates for the user's Firestore document, including the new `name` and the obtained `profileImageUrl` (or an empty string/null if no image was uploaded or if an existing one was removed).
        *   Call the existing `updateUserProfile(userId, updates)` function in `UserRepository.kt` to update the user's document in Firestore.
    *   Modify the existing `getUserById` or add a new function to retrieve the `User` object, which will now contain the `profileImageUrl`.
6.  **Create Profile UI:**
    *   Create a new Fragment file: `app/src/main/java/com/homeostasis/app/ui/profile/ProfileSettingsFragment.kt`.
    *   Create a corresponding layout file (e.g., `fragment_profile_settings.xml`).
    *   The layout will include:
        *   An `EditText` for the user's name.
        *   An `ImageView` to display the profile picture.
        *   A button to select a new profile picture.
        *   A button to save the changes.
    *   Implement the UI logic in `ProfileSettingsFragment.kt`, including handling button clicks and displaying the current profile information.
7.  **Connect UI to Data:**
    *   Create a new ViewModel file: `app/src/main/java/com/homeostasis/app/ui/profile/ProfileSettingsViewModel.kt`.
    *   Inject `UserRepository` and potentially the image processing utility into this ViewModel using Hilt.
    *   The ViewModel will expose `LiveData` or `StateFlow` for the user's current profile information (name and picture URL).
    *   Implement functions in the ViewModel to:
        *   Load the current user's profile data from `UserRepository`.
        *   Handle the result of the image selection Activity Result API.
        *   Trigger the image processing logic.
        *   Call the `UserRepository.updateUserProfileWithPicture` function to save the profile changes, including the processed image.
8.  **Update Navigation:**
    *   Modify `app/src/main/java/com/homeostasis/app/MainActivity.kt` to:
        *   Add the `ProfileSettingsFragment` to the Navigation Component's navigation graph.
        *   Update the bottom navigation bar to include a destination for the profile settings, linked to the user icon.
9.  **Display Profile Picture:**
    *   Use an image loading library (e.g., Glide or Coil) in `ProfileSettingsFragment.kt` and potentially in `MainActivity.kt` (for the bottom navigation icon) to load the profile picture from the URL.
    *   Apply a circular transformation to the ImageView to display the picture in a circle.

## Architecture Diagram

```mermaid
graph TD
    A[ProfileSettingsFragment] --> B(ProfileSettingsViewModel)
    B --> C(UserRepository)
    C --> D(FirebaseFirestore)
    C --> E(FirebaseStorageRepository)
    E --> F(Firebase Cloud Storage)
    B --> G(Image Processing Utility)
    G --> A
    A --> H(Gallery/Camera)
    H --> G
    MainActivity --> A
    MainActivity --> B
    UserRepository --> I(User Data Model)
    FirebaseStorageRepository --> J(Profile Picture File)
    J --> F