# Database Implementation

The database implementation consists of a local DB (Room) and a remote DB (Firebase). All activities are performed on the local DB and then synced with the remote DB. The remote DB is only ever updated by the sync process.

## Syncing Process

Here's how the syncing should occur:

1.  **Local Write:** When a task is completed, the `TaskHistory` object is first written to the local DB using the `TaskHistoryDao.insert()` method.
2.  **Remote Write:** The remote DB is only ever updated by the sync process.
3.  **Conflict Resolution:** In case of a conflict between the local and remote DBs, the remote DB should be the source of truth. This means that if there is a discrepancy between the local and remote DBs, the local DB should be updated to match the remote DB.
4.  **Periodic Sync:** The local DB should be periodically synced with the remote DB to ensure that the local DB is up-to-date. This can be done using a background service or a scheduled task.

## Diagram

Here's a diagram to illustrate the syncing process:

```mermaid
sequenceDiagram
    participant User
    participant TaskListFragment
    participant TaskHistoryRepository
    participant LocalDB
    participant RemoteDB

    User->>TaskListFragment: Completes Task
    TaskListFragment->>TaskHistoryRepository: recordTaskCompletion(task, userId)    
    TaskHistoryRepository->>LocalDB: taskHistoryDao.insert(taskHistory)
    
    TaskHistoryRepository-->>TaskListFragment: Success/Failure
    TaskListFragment->>User: Task Completion Acknowledged
    loop Periodic Sync
        LocalDB-->>TaskHistoryRepository: read local DB
        TaskHistoryRepository->>RemoteDB: Update Task History as required  
        RemoteDB->>TaskHistoryRepository: Get Latest Task History              
    end