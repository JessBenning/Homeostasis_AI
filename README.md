# Homeostasis

A family-oriented task management app with Firebase backend.

## Overview

Homeostasis is an Android application designed to help families manage tasks and shopping lists collaboratively. The app provides a simple, intuitive interface for creating tasks, assigning points, tracking completion, and maintaining shared shopping lists.

## Features

- **Task Management**: Create, view, complete, and track tasks with point values and categories
- **Task History**: Track who completed tasks and when
- **Leaderboard**: View family members' scores with rankings
- **Shopping Lists**: Maintain multiple shared shopping lists with history of previous items
- **User Profiles**: Create profiles with names and profile images
- **Categories**: Organize tasks with customizable categories (colors and icons)
- **Score Threshold Reset**: Automatically reset scores when all users reach a threshold
- **Real-time Synchronization**: Changes sync across all family members' devices
- **Offline Support**: Use the app even when offline, with automatic synchronization when back online

## Technical Stack

- **Frontend**: Native Android with Kotlin
- **UI Framework**: Material Design Components
- **Architecture**: MVVM (Model-View-ViewModel)
- **Backend**: Firebase (Authentication, Firestore, Storage)
- **Local Storage**: Room Database
- **Dependency Injection**: Hilt
- **Navigation**: Jetpack Navigation Component
- **Asynchronous Programming**: Kotlin Coroutines
- **Image Loading**: Glide

## Project Structure

- `/app` - Android application source code
- `/docs` - Project documentation and planning materials

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 17 or newer
- Firebase account

### Setup

1. Clone the repository:
```
git clone https://github.com/yourusername/Homeostasis_AI.git
```

2. Open the project in Android Studio

3. Create a Firebase project and add an Android app:
   - Package name: `com.homeostasis.app`
   - Download the `google-services.json` file and place it in the `app/` directory

4. Enable Firebase services:
   - Authentication (Email/Password)
   - Cloud Firestore
   - Storage

5. Build and run the app

## Documentation

For detailed documentation, see the `/docs` directory:

- [Project Plan](docs/project_plan.md)
- [Architecture Summary](docs/architecture_summary.md)
- [UI Design](docs/ui_design_updates.md)
- [Data Models](docs/data_model_updates.md)
- [Android Framework Options](docs/android_framework_options.md)
- [Android Studio Workflow](docs/android_studio_workflow.md)

## License

[MIT License](LICENSE)