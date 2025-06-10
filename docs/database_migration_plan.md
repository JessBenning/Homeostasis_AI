# Database Migration Plan: Supporting Multiple Household Groups

This document outlines the plan for migrating the "Homeostasis AI" Android application's database to support multiple household groups. The current implementation uses a single database for all users, which is not suitable for multi-tenancy. This plan proposes adding a `householdGroupId` column to the existing tables to provide better data isolation and scalability.

## 1. Overview

The goal is to modify the database schema to support multiple household groups. This will involve:

*   Adding a `householdGroupId` column to the existing tables (`Task`, `User`, `TaskHistory`).
*   Modifying the DAOs to include the `householdGroupId` in all queries.
*   Updating the repositories to pass the `householdGroupId` to the DAOs.
*   Modifying the UI to allow the user to select a household group.
*   Updating the Firebase Sync Manager to use dynamic Firestore collection paths.
*   Implementing a database migration strategy to move existing data to the new column.

## 2. Detailed Plan

### 2.1. Database Schema Modification

*   **Adding `householdGroupId` Column:** The `AppDatabase` class needs to be modified to add a `householdGroupId` column to the existing tables (`Task`, `User`, `TaskHistory`). This can be achieved by:
    *   Adding a `householdGroupId` property to the `Task`, `User`, and `TaskHistory` data classes.
    *   Creating a Room migration to add the new column to the database tables.
*   **`householdGroupId` Generation:** The `householdGroupId` will be a UUID (Universally Unique Identifier) to ensure uniqueness across all household groups.
*   **Room Database Versioning:** The Room database version needs to be incremented, and a migration needs to be provided.

### 2.2. DAO Modification

*   **Including `householdGroupId` in Queries:** The DAOs (`TaskDao`, `UserDao`, etc.) need to be modified to include the `householdGroupId` in all queries. This can be achieved by:
    *   Passing the `householdGroupId` as a parameter to all DAO methods.
    *   Adding a `WHERE householdGroupId = :householdGroupId` clause to all SQL queries.
    *   Example:
        ```kotlin
        @Query("SELECT * FROM tasks WHERE id = :taskId AND householdGroupId = :householdGroupId")
        fun getTaskById(householdGroupId: String, taskId: String): Task
        ```

### 2.3. Repository Modification

*   **`householdGroupId` Propagation:** The repositories need to receive the `householdGroupId` from the UI layer and pass it to the DAOs.
*   This ensures that the correct data is accessed for each household group.

### 2.4. UI Modification

*   **Household Group Selection:** The UI needs to provide a mechanism for the user to select a household group. This could be a dropdown menu, a list of groups, or any other suitable UI element.
*   **`householdGroupId` Storage:** The selected `householdGroupId` needs to be stored in a persistent manner (e.g., using SharedPreferences or DataStore) so that it can be retrieved when the application is restarted.
*   **`householdGroupId` Passing:** The selected `householdGroupId` needs to be passed to the repositories when data is queried or modified.

### 2.5. Firebase Sync Manager Modification

*   **Dynamic Firestore Collection Paths:** The `FirebaseSyncManager` needs to be modified to use dynamic Firestore collection paths based on the `householdGroupId`.
*   This ensures that data is synced to the correct Firestore collections for each household group.
*   The Firestore collection paths will follow the convention: `householdGroups/<householdGroupId>/tasks`, `householdGroups/<householdGroupId>/users`, etc.

### 2.6. Database Migration

*   **Migration Strategy:** A database migration strategy needs to be implemented to move existing data to the new column. This could involve:
    *   Adding the `householdGroupId` column to the existing tables.
    *   Updating all existing rows with the appropriate `householdGroupId`.
*   **Migration Code:** The migration code needs to be carefully written to ensure that data is migrated correctly and without any data loss.

## 3. Diagram

```mermaid
graph LR
    A[UI] --> B(Repository);
    B --> C(DAO);
    C --> D{Room Database};
    A --> E(Firebase Sync Manager);
    E --> F{Firestore};
    A --> G{Household Group Selection};
    G --> B;
    G --> E;
    D -- householdGroupId Column --> H{Household Group ID};
    F -- Dynamic Collection Paths --> H;
    style A fill:#f9f,stroke:#333,stroke-width:2px
    style B fill:#ccf,stroke:#333,stroke-width:2px
    style C fill:#ccf,stroke-width:2px
    style D fill:#fcf,stroke:#333,stroke-width:2px
    style E fill:#ccf,stroke:#333,stroke-width:2px
    style F fill:#fcf,stroke:#333,stroke-width:2px
    style G fill:#f9f,stroke:#333,stroke-width:2px
    style H fill:#ccf,stroke:#333,stroke-width:2px