mermaid
sequenceDiagram
actor User
participant TLF as TaskListFragment (UI)
participant TLVM as TaskListViewModel
participant TDAO as TaskDao (Room DB)
participant TR as TaskRepository (Firestore Direct)
participant FSM as FirebaseSyncManager
participant FS as Firestore (Remote)

    %% Scenario 1: Loading Tasks
    User->>TLF: Opens Task List Screen
    TLF->>TLVM: Requests active tasks
    activate TLVM
    TLVM->>TDAO: getActiveVisibleTasks() (Flow)
    activate TDAO
    TDAO-->>TLVM: Flow<List<Task>> (local tasks)
    deactivate TDAO
    TLVM-->>TLF: StateFlow<List<Task>> updates
    deactivate TLVM
    TLF->>User: Displays tasks

    %% Scenario 2: Adding a New Task
    User->>TLF: Clicks 'Add Task' & enters details
    TLF->>TLVM: addTask(newTaskDetails)
    activate TLVM
    TLVM->>TDAO: upsertTask(newTask.copy(needsSync=true, isDeletedLocally=false))
    activate TDAO
    Note over TDAO: New task saved locally, marked for sync
    TDAO-->>TLVM: (Returns)
    deactivate TDAO
    deactivate TLVM
    %% Note: UI updates via observing the Flow from getActiveVisibleTasks()

    %% Scenario 3: Editing an Existing Task
    User->>TLF: Edits a task & saves
    TLF->>TLVM: updateTask(updatedTaskDetails)
    activate TLVM
    TLVM->>TDAO: upsertTask(updatedTask.copy(needsSync=true, lastModifiedAt=now()))
    activate TDAO
    Note over TDAO: Task updated locally, marked for sync
    TDAO-->>TLVM: (Returns)
    deactivate TDAO
    deactivate TLVM
    %% Note: UI updates via observing the Flow from getActiveVisibleTasks()

    %% Scenario 4: Deleting a Task (Soft Delete Locally First)
    User->>TLF: Swipes to delete a task
    TLF->>TLVM: deleteTask(taskToDelete)
    activate TLVM
    TLVM->>TDAO: upsertTask(taskToDelete.copy(isDeletedLocally=true, needsSync=true, lastModifiedAt=now()))
    activate TDAO
    Note over TDAO: Task marked as 'isDeletedLocally' & for sync
    TDAO-->>TLVM: (Returns)
    deactivate TDAO
    deactivate TLVM
    %% Note: UI updates via observing the Flow from getActiveVisibleTasks() (task disappears as isDeletedLocally=true)


    %% Scenario 5: Background Sync (Simplified - FSM activities)

    %% 5a: Remote (Firestore) changes to Local (Room) via FSM Listener
    activate FSM
    FSM->>FS: addSnapshotListener(Task.COLLECTION)
    activate FS
    FS-->>FSM: Receives DocumentChanges (e.g., Task Added/Modified/Removed on another device)
    deactivate FS
    loop For each DocumentChange
        FSM->>TDAO: getTaskById(remoteTask.id)  // Check if exists, handle conflicts
        activate TDAO
        TDAO-->>FSM: Optional<LocalTask>
        deactivate TDAO
        FSM->>TDAO: upsertTask(taskFromRemote.copy(needsSync=false, isDeletedLocally=false)) or hardDeleteTaskFromRoom(remoteTask)
        activate TDAO
        Note over TDAO: Local DB updated based on remote change
        TDAO-->>FSM: (Returns)
        deactivate TDAO
    end
    %% Note: This triggers UI updates if the TaskDao Flow being observed by ViewModel is affected

    %% 5b: Local (Room) changes to Remote (Firestore) via FSM Reconciliation/Reactive Flows
    %% This could be triggered by init or periodically/reactively
    FSM->>TDAO: getAllTasksFromRoomSnapshot() (for one-time reconciliation)
    activate TDAO
    TDAO-->>FSM: List<LocalTask>
    deactivate TDAO
    loop For each localTask where needsSync=true
        alt localTask.isDeletedLocally == true
            FSM->>TR: softDeleteTaskInFirestore(localTask.id)
            activate TR
            TR->>FS: update(isDeleted=true, lastModifiedAt=serverTimestamp)
            activate FS
            FS-->>TR: Success/Failure
            deactivate FS
            TR-->>FSM: Success/Failure
            deactivate TR
            opt if Success
                FSM->>TDAO: upsertTask(localTask.copy(isDeleted=true, isDeletedLocally=false, needsSync=false))
                activate TDAO
                TDAO-->>FSM: (Returns)
                deactivate TDAO
            end
        else localTask.isDeletedLocally == false (Create or Update)
            FSM->>TR: createOrUpdateTaskInFirestore(localTask)
            activate TR
            TR->>FS: set(localTaskData, merge=true)
            activate FS
            FS-->>TR: Success/Failure
            deactivate FS
            TR-->>FSM: Success/Failure
            deactivate TR
            opt if Success
                FSM->>TDAO: upsertTask(localTask.copy(needsSync=false))
                activate TDAO
                TDAO-->>FSM: (Returns)
                deactivate TDAO
            end
        end
    end
    deactivate FSM