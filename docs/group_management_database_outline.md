# Group Management Database Outline

This document outlines the necessary database changes (Room and Firestore) to support the group management user stories, including the refined approach for handling tasks during group deletion.

## 1. Group Entity/Collection

*   **Purpose:** To store information about each household group.
*   **Room Database (`AppDatabase`)**:
    *   **New Entity:** `Group`
    *   **Fields:**
        *   `id`: String (Primary Key) - Unique identifier for the group.
        *   `name`: String - The name of the household group.
        *   `ownerId`: String - The ID of the user who created and owns the group.
        *   `createdAt`: Timestamp - The timestamp when the group was created.
        *   `lastModifiedAt`: Timestamp - The timestamp of the last modification to the group details.
        *   `needsSync`: Boolean - Flag to indicate if the local group data needs to be synced to Firestore.
    *   **New DAO:** `GroupDao` with standard CRUD operations (Insert, Update, Delete, Get by ID, Get all).

*   **Firestore Database:**
    *   **New Collection:** `groups`
    *   **Documents:** Each document will represent a single household group.
    *   **Fields:**
        *   `id`: String - Matches the Room entity ID.
        *   `name`: String - The name of the group.
        *   `ownerId`: String - The ID of the group owner.
        *   `createdAt`: Timestamp - Creation timestamp.
        *   `lastModifiedAt`: Timestamp - Last modified timestamp.

## 2. User Entity/Collection Modifications

*   **Purpose:** To link users to their current household group and define their role within the group.
*   **Room Database (`AppDatabase`)**:
    *   **Modify Existing Entity:** `User`
    *   **Fields:**
        *   `householdGroupId`: String (already exists) - The ID of the group the user belongs to. This will now explicitly link to the `Group` entity.

*   **Firestore Database:**
    *   **Modify Existing Collection:** `users`
    *   **Fields:**
        *   `householdGroupId`: String (already exists) - Matches the Room entity field.

## 3. Task Entity/Collection Modifications

*   **Purpose:** To associate tasks with a specific household group and link them to a conceptual owner (the group owner for tasks within a group).
*   **Room Database (`AppDatabase`)**:
    *   **Modify Existing Entity:** `Task`
    *   **Fields:**
        *   Remove the existing `createdBy` field (which currently stores a name).
        *   Add a new `ownerId` field (String). This field will store the User ID of the task's owner. For tasks created within a group, this will be the group owner's ID.
        *   `householdGroupId`: String (already exists) - Confirms the association with a group.
    *   **Task Creation Logic (within a group):**
        *   When a user creates a task while they are currently in a household group, set the `ownerId` of the newly created task to the `ownerId` of that household group.

*   **Firestore Database:**
    *   **Modify Existing Collection:** `tasks`
    *   **Fields:**
        *   Remove the existing `createdBy` field.
        *   Add a new `ownerId` field (String).
        *   `householdGroupId`: String (already exists) - Confirms the association with a group.

## 4. TaskHistory Entity/Collection Modifications

*   **Purpose:** To ensure task history is associated with the correct household group.
*   **Room Database (`AppDatabase`)**:
    *   **Modify Existing Entity:** `TaskHistory`
    *   **Fields:**
        *   `householdGroupId`: String (already exists) - Confirms the association with a group.

*   **Firestore Database:**
    *   **Modify Existing Collection:** `task_history`
    *   **Fields:**
        *   `householdGroupId`: String (already exists) - Confirms the association with a group.

## 5. Implications of Group Deletion (with Option to Keep Tasks)

*   **Group:** The Group document/entity is deleted.
*   **User:** Members' `householdGroupId` is updated (cleared/defaulted).
*   **TaskHistory:** All associated TaskHistory documents/entities are permanently deleted.
*   **Task:** If the owner chooses to keep tasks, associated Task documents/entities have their `householdGroupId` cleared/updated. The `ownerId` (set to the former group owner) remains unchanged. If the owner chooses NOT to keep tasks, all associated Task documents/entities are permanently deleted.

## 6. Syncing Considerations

*   The `FirebaseSyncManager` will require significant updates to handle the synchronization of the new `Group` data.
*   Sync logic will need to be implemented for the new `ownerId` field in the `Task` entity.
*   A specific process within the sync manager (or triggered by the sync manager) will be needed to handle the cascading deletions and updates required during group deletion, ensuring data consistency across all clients and Firestore.