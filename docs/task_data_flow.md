# Task Data Flow

This document describes the data flow for tasks in the Homeostasis app.

## Components

*   UI (User Interface)
*   ViewModel
*   Local Room Database
*   FirebaseSyncManager
*   TaskRepository
*   Remote Firebase Database

## Interactions

*   **UI (User Interface):** The user interacts with the UI to perform actions related to tasks, such as creating a new task, updating an existing task, deleting a task, or viewing a list of tasks.
*   **ViewModel:** The UI interacts with a ViewModel, which is responsible for handling the UI logic and data.
*   **Local Room Database:** The ViewModel interacts with the Local Room Database to perform operations on the task data.
    *   To **create** a task, the ViewModel calls the `insertTask()` function in the `TaskDao`, which adds the task to the Local Room Database.
    *   To **update** a task, the ViewModel calls the `updateTask()` function in the `TaskDao`, which updates the task in the Local Room Database.
    *   To **delete** a task, the ViewModel calls the `deleteTask()` function in the `TaskDao`, which deletes the task from the Local Room Database.
    *   To **retrieve** tasks, the ViewModel calls the `getAllTasks()` function in the `TaskDao`, which retrieves the tasks from the Local Room Database.
*   **FirebaseSyncManager:** The `FirebaseSyncManager` is responsible for synchronizing the data between the Local Room Database and the Remote Firebase Database.
    *   The `FirebaseSyncManager` periodically reads all tasks from the Local Room Database and uploads them to the Remote Firebase Database to ensure that the data is synchronized.
    *   The `FirebaseSyncManager` also listens for changes in the "tasks" collection in the Remote Firebase Database. When a change occurs (e.g., a new task is added, an existing task is updated, or a task is deleted), the `FirebaseSyncManager` receives a notification.
    *   The `FirebaseSyncManager` then updates the Local Room Database to reflect the changes in the Remote Firebase Database.
*   **TaskRepository:** The `TaskRepository` is only used by the `FirebaseSyncManager` to interact with the Remote Firebase Database.
    *   The `FirebaseSyncManager` calls the `createTask()` function in the `TaskRepository` to add a new task to the Remote Firebase Database.
    *   The `FirebaseSyncManager` calls the `updateTask()` function in the `TaskRepository` to update an existing task in the Remote Firebase Database.
    *   The `FirebaseSyncManager` calls the `softDeleteTask()` function in the `TaskRepository` to delete a task from the Remote Firebase Database.
    *   The `FirebaseSyncManager` calls the `getTasksByCategory()` or `getActiveTasks()` function in the `TaskRepository` to retrieve tasks from the Remote Firebase Database.
*   **Remote Firebase Database:** The Remote Firebase Database stores the task data remotely.

## Sequence Diagram

```mermaid
sequenceDiagram
    participant UI
    participant ViewModel
    participant LocalDB
    participant FirebaseSyncManager
    participant TaskRepository
    participant RemoteDB

    UI->>ViewModel: Create Task (all timestamps set to now)
    ViewModel->>LocalDB: createTask(task) via taskDao
    LocalDB-->>ViewModel: Success
    ViewModel-->>UI: Task created successfully
    
    UI->>ViewModel: Update Task (modified timestamps set to now)
    ViewModel->>LocalDB: updateTask(task) via taskDao
    LocalDB-->>ViewModel: Success
    ViewModel-->>UI: Task updated successfully

    FirebaseSyncManager->>LocalDB: getAllTasks()
    LocalDB-->>FirebaseSyncManager: Tasks (no data modified)
    FirebaseSyncManager->>TaskRepository: createTask(task) if new updateTask(task) if modified via taskRepository
    TaskRepository->>RemoteDB: Add or modify task in the "tasks" collection
    RemoteDB-->>TaskRepository: Success

    RemoteDB->>FirebaseSyncManager: Task created (no data modified)
    FirebaseSyncManager->>LocalDB: createTask(task) via taskDao
    LocalDB-->>FirebaseSyncManager: Success

    UI->>ViewModel: Get Tasks
    ViewModel->>LocalDB: getAllTasks()
    LocalDB-->>ViewModel: Tasks
    ViewModel-->>UI: Display Tasks