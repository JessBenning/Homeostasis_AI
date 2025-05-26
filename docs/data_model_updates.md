# Data Model Updates for Homeostasis App

Based on the updated requirements, the following changes to the data models are recommended:

## 1. Updated User Model

```
User {
    id: String
    name: String
    profileImageUrl: String
    createdAt: Timestamp
    lastActive: Timestamp
}
```

## 2. Updated Task Model

```
Task {
    id: String
    title: String
    description: String
    points: Integer
    categoryId: String  // New field to link to category
    createdBy: String (User ID)
    createdAt: Timestamp
}
```

## 3. New TaskHistory Model

```
TaskHistory {
    id: String
    taskId: String  // Reference to the task
    userId: String  // User who completed the task
    completedAt: Timestamp  // When the task was completed
    pointValue: Integer  // Store the point value at time of completion
}
```

## 4. New Category Model

```
Category {
    id: String
    name: String
    color: String  // For UI representation
    icon: String   // Icon identifier for visual representation
    createdBy: String (User ID)
    createdAt: Timestamp
}
```

## 5. Updated Shopping List Model

```
ShoppingList {
    id: String
    name: String
    createdBy: String (User ID)
    createdAt: Timestamp
}
```

## 6. Updated Shopping Item Model

```
ShoppingItem {
    id: String
    name: String
    category: String (optional)
    lastUsed: Timestamp
    usageCount: Integer
    createdBy: String (User ID)
    createdAt: Timestamp
}
```

## 7. New Shopping List Item Model

```
ShoppingListItem {
    id: String
    itemId: String  // Reference to ShoppingItem
    listId: String  // Reference to ShoppingList
    quantity: String (optional)
    notes: String (optional)
    addedBy: String (User ID)
    addedAt: Timestamp
    isChecked: Boolean
    checkedBy: String (User ID)
    checkedAt: Timestamp
}
```

## 8. Updated Firestore Database Structure

```
/users/{userId}
    - name: String
    - profileImageUrl: String
    - createdAt: Timestamp
    - lastActive: Timestamp

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

/taskHistory/{historyId}
    - taskId: String  // Reference to the task
    - userId: String  // User who completed the task
    - completedAt: Timestamp  // When the task was completed
    - pointValue: Integer  // Store the point value at time of completion

/shoppingLists/{listId}
    - name: String
    - createdBy: String (User ID)
    - createdAt: Timestamp

/shoppingItems/{itemId}
    - name: String
    - category: String (optional)
    - lastUsed: Timestamp
    - usageCount: Integer
    - createdBy: String (User ID)
    - createdAt: Timestamp

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