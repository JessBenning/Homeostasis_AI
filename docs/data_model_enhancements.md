# Data Model Enhancements for Homeostasis App

After reviewing the updated UI design, the following enhancements to the data models are needed to fully support the new features and interactions:

## 1. Task Model Enhancements

```diff
Task {
    id: String
    title: String
    description: String
    points: Integer
    categoryId: String
    createdBy: String (User ID)
    createdAt: Timestamp
+   lastModifiedAt: Timestamp  // To track when a task was last edited
+   isDeleted: Boolean  // Soft delete flag for tasks
}
```

## 2. New Settings Model

```
Settings {
    id: String  // Usually a single document with a fixed ID like "app_settings"
    cloudSync: {
        enabled: Boolean
        lastSyncTime: Timestamp
        syncFrequency: String  // e.g., "hourly", "daily", "manual"
    }
    resetHistory: {
        lastResetTime: Timestamp
        resetBy: String (User ID)
    }
}
```

## 3. User Settings Model

```
UserSettings {
    userId: String
    notificationsEnabled: Boolean
    theme: String  // e.g., "light", "dark", "system"
    cloudBackup: {
        enabled: Boolean
        lastBackupTime: Timestamp
        autoBackup: Boolean
    }
}
```

## 4. TaskHistory Model Enhancements

```diff
TaskHistory {
    id: String
    taskId: String  // Reference to the task
    userId: String  // User who completed the task
    completedAt: Timestamp  // When the task was completed
+   pointValue: Integer  // Store the point value at time of completion
+   customCompletionDate: Date  // To support custom date selection in completion dialog
+   isDeleted: Boolean  // Soft delete flag for history entries
+   lastModifiedAt: Timestamp  // To track when a history entry was last edited
}
```

## 5. Updated Firestore Database Structure

```diff
/users/{userId}
    - name: String
    - profileImageUrl: String
    - createdAt: Timestamp
    - lastActive: Timestamp

/userSettings/{userId}
    - notificationsEnabled: Boolean
    - theme: String
    - cloudBackup: Map

/categories/{categoryId}
    - name: String
    - color: String
    - icon: String
    - createdBy: String (User ID)
    - createdAt: Timestamp

/tasks/{taskId}
    - title: String
    - description: String
    - points: Integer
    - categoryId: String
    - createdBy: String (User ID)
    - createdAt: Timestamp
+   - lastModifiedAt: Timestamp
+   - isDeleted: Boolean

/taskHistory/{historyId}
    - taskId: String
    - userId: String
    - completedAt: Timestamp
+   - pointValue: Integer
+   - customCompletionDate: Date
+   - isDeleted: Boolean
+   - lastModifiedAt: Timestamp

/shoppingLists/{listId}
    - name: String
    - createdBy: String (User ID)
    - createdAt: Timestamp
+   - lastModifiedAt: Timestamp  // To track when a list was last edited
+   - isDeleted: Boolean  // Soft delete flag for lists

/shoppingItems/{itemId}
    - name: String
    - category: String (optional)
    - lastUsed: Timestamp
    - usageCount: Integer
    - createdBy: String (User ID)
    - createdAt: Timestamp
+   - lastModifiedAt: Timestamp  // To track when an item was last edited

/shoppingListItems/{listItemId}
    - itemId: String  // Reference to ShoppingItem
    - listId: String  // Reference to ShoppingList
    - quantity: String (optional)
    - notes: String (optional)
    - addedBy: String (User ID)
    - addedAt: Timestamp
    - isChecked: Boolean
    - checkedBy: String (User ID)
    - checkedAt: Timestamp
+   - lastModifiedAt: Timestamp  // To track when a list item was last edited
+   - isDeleted: Boolean  // Soft delete flag for list items

+ /settings/appSettings
+   - cloudSync: Map
+   - resetHistory: Map
```

## 6. Additional Considerations

### 6.1 Soft Delete Implementation

For both tasks and history entries, we're using a soft delete approach (isDeleted flag) rather than actually removing the documents. This provides several benefits:

1. Ability to restore accidentally deleted items
2. Preservation of historical data for potential analytics
3. Simpler implementation of undo functionality

In the application code, queries should include a filter for `isDeleted == false` to exclude soft-deleted items from normal views.

### 6.2 Time Precision

Since the UI now displays time information for task completions, ensure that Timestamp fields store both date and time components with sufficient precision.

### 6.3 Custom Completion Dates

The task completion dialog now allows selecting a custom date. This should be handled in the application logic:

1. Default the date picker to the current date/time
2. When a user selects a different date, use that date combined with the current time
3. Store the complete timestamp in the completedAt field and the custom date in the customCompletionDate field
4. For display purposes, format the timestamp appropriately to show both date and time

### 6.4 Cloud Storage Settings

The new Settings section in the UI includes cloud storage options. This requires:

1. User-specific settings for enabling/disabling cloud backup
2. App-level settings for sync frequency and other global parameters
3. Timestamps to track last sync/backup times
4. Appropriate security rules to protect these settings

These enhancements to the data model will ensure that all the UI functionality is properly supported by the underlying data structure.

### 6.5 Shopping List Item Mapping

The shopping list design now uses a mapping approach between items and lists:

1. **Base Items**: The ShoppingItem collection stores the catalog of unique items with usage tracking
2. **List Item Mapping**: The ShoppingListItem collection maps items to specific lists with additional properties
3. **Soft Delete**: Both shopping lists and list items support soft deletion for undo functionality
4. **Performance Optimization**:
   - Consider denormalizing item names into ShoppingListItem for faster list rendering
   - Use composite indexes for efficient queries across ShoppingListItem and ShoppingItem
   - Implement caching for frequently accessed shopping lists and items