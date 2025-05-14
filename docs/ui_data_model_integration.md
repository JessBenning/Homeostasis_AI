# UI and Data Model Integration for Homeostasis App

This document explains how the UI components will interact with the revised data model structure, focusing on the dynamic score calculation approach and the separation of task and task history data.

## 1. Home Screen (Task List)

### Data Source
- Task cards display information from both the `Task` model and the most recent entry in the `TaskHistory` model for each task.

### Implementation Details
```
For each task in /tasks collection:
  1. Fetch task details (title, description, points, category)
  2. Query /taskHistory collection for the most recent entry where taskId = current task's id
  3. If history entry exists, display "Last done: [date] [time] by [user name]"
  4. If no history entry exists, display "Last done: Never"
```

### UI Considerations
- Task cards should be designed to handle both cases (tasks with and without completion history)
- The "Last done" information should be updated in real-time when a task is completed
- Swipe actions should be implemented for edit and delete functionality

## 2. Task Completion Dialog

### Data Source
- When a user completes a task, a new entry is created in the `TaskHistory` model
- The custom date selection is stored in the `customCompletionDate` field
- The task's current point value is stored in the `pointValue` field

### Implementation Details
```
When user marks a task as complete:
  1. Show completion dialog with date picker defaulted to current date
  2. When user confirms:
     a. Fetch the current task to get its point value
     b. Create new TaskHistory entry with:
        - taskId = completed task's id
        - userId = current user's id
        - completedAt = current timestamp
        - customCompletionDate = selected date from picker (or current date if unchanged)
        - pointValue = task's current point value
     c. Update UI to show updated task history
```

### UI Considerations
- The date picker should default to the current date
- The completion timestamp should include both date and time information
- The UI should show appropriate feedback (confetti animation, score update)

## 3. Leaderboard Screen

### Data Source
- User rankings are calculated dynamically from the `TaskHistory` model
- Task completion history comes from the `TaskHistory` model

### Implementation Details
```
For leaderboard display:
  1. Fetch all users from /users collection
  2. For each user, calculate their score:
     a. Query /taskHistory collection where userId = current user's id and isDeleted = false
     b. Sum the pointValue field for all matching entries
  3. Sort users by calculated score (descending)
  4. Calculate point differences between users
  5. Apply appropriate ranking (1st, 2nd, 3rd, etc.)

For task history section:
  1. Query /taskHistory collection ordered by completedAt (descending)
  2. Join with /tasks collection to get task titles
  3. Join with /users collection to get user names
```

### UI Considerations
- The history section should support swipe actions for edit/delete operations
- Editing a history entry updates the `lastModifiedAt` field
- Deleting a history entry sets the `isDeleted` flag to true (soft delete)
- Consider caching calculated scores to improve performance

## 4. User Profile Screen

### Data Source
- User details come from the `User` model
- User score is calculated dynamically from the `TaskHistory` model
- Task history for the specific user comes from the `TaskHistory` model

### Implementation Details
```
For user profile:
  1. Fetch user details from /users collection
  2. Calculate user's score:
     a. Query /taskHistory collection where userId = current user's id and isDeleted = false
     b. Sum the pointValue field for all matching entries
  3. Calculate user's rank by comparing their score to other users

For user's task history:
  1. Query /taskHistory collection where userId = current user's id and isDeleted = false
  2. Order by completedAt (descending)
  3. Join with /tasks collection to get task titles
```

### UI Considerations
- The profile screen should show the user's ranking relative to other users
- Task history should be limited to the current user's completed tasks
- Consider implementing filters for task history (e.g., by date range, category)

## 5. Shopping Lists and Items

### Data Source
- Shopping lists come from the `ShoppingList` model
- Base item definitions come from the `ShoppingItem` model
- List-specific items come from the `ShoppingListItem` model that maps items to lists

### Implementation Details
```
For shopping lists screen:
  1. Query /shoppingLists collection where isDeleted = false
  2. For each list, count items in /shoppingListItems where listId = current list's id
  3. Count checked vs. unchecked items

For shopping list detail:
  1. Fetch list details from /shoppingLists collection
  2. Query /shoppingListItems collection where listId = current list's id and isDeleted = false
  3. Join with /shoppingItems collection to get item details
  4. Query /shoppingItems collection ordered by usageCount (descending) for quick-add suggestions
```

### UI Considerations
- Shopping lists should display item counts and completion status
- Shopping list detail should support quick-add from global item catalog
- Items should be checkable/uncheckable with appropriate visual feedback
- Support for item quantity and notes in the UI
- Consider implementing auto-complete when adding new items

### Adding Items to Lists
```javascript
// Example of adding an item to a shopping list
function addItemToList(listId, itemNameOrId, quantity = null, notes = null) {
  // Check if we have an ID or a name
  if (typeof itemNameOrId === 'string' && itemNameOrId.length < 30) {
    // Likely a name, search for existing item
    return firebase.firestore()
      .collection('shoppingItems')
      .where('name', '==', itemNameOrId)
      .limit(1)
      .get()
      .then(snapshot => {
        if (snapshot.empty) {
          // Create new item
          return createNewShoppingItem(itemNameOrId).then(itemId => {
            return addItemToListById(listId, itemId, quantity, notes);
          });
        } else {
          // Use existing item
          const itemId = snapshot.docs[0].id;
          return addItemToListById(listId, itemId, quantity, notes);
        }
      });
  } else {
    // Already have an item ID
    return addItemToListById(listId, itemNameOrId, quantity, notes);
  }
}
```

## 6. Category Management

### Data Source
- Categories come from the `Category` model

### Implementation Details
```
For category management:
  1. Query /categories collection
  2. Display name and color for each category
  3. Support add/edit/delete operations
```

### UI Considerations
- Category colors should be used consistently throughout the app
- Deleting a category should handle orphaned tasks (either reassign or show warning)

## 7. Performance Optimization Strategies

### Caching Calculated Scores
```javascript
// Example of caching calculated scores
const userScoreCache = new Map();
const CACHE_EXPIRY = 5 * 60 * 1000; // 5 minutes

function getCachedUserScore(userId) {
  const cachedData = userScoreCache.get(userId);
  const now = Date.now();
  
  if (cachedData && (now - cachedData.timestamp < CACHE_EXPIRY)) {
    return Promise.resolve(cachedData.score);
  }
  
  return calculateUserScore(userId).then(score => {
    userScoreCache.set(userId, {
      score,
      timestamp: now
    });
    return score;
  });
}
```

### Denormalization for Frequent Queries
Consider denormalizing certain data to improve query performance:

1. Store categoryId in TaskHistory entries to avoid joins when filtering by category
2. Store taskTitle in TaskHistory entries to avoid joins when displaying task history
3. Store userName and userProfileUrl in TaskHistory entries for displaying who completed tasks

### Pagination for Large Data Sets
```javascript
// Example of paginated task history query
function getPaginatedTaskHistory(userId, pageSize = 10, lastDoc = null) {
  let query = firebase.firestore()
    .collection('taskHistory')
    .where('userId', '==', userId)
    .where('isDeleted', '==', false)
    .orderBy('completedAt', 'desc')
    .limit(pageSize);
    
  if (lastDoc) {
    query = query.startAfter(lastDoc);
  }
  
  return query.get().then(snapshot => {
    const history = [];
    snapshot.forEach(doc => {
      history.push({
        id: doc.id,
        ...doc.data()
      });
    });
    
    const lastVisible = snapshot.docs[snapshot.docs.length - 1];
    
    return {
      history,
      lastVisible
    };
  });
}
```

## 8. Key Considerations for Implementation

1. **Real-time Updates**: Use Firestore listeners to ensure UI components update in real-time when data changes.

2. **Offline Support**: Ensure the app works offline by properly configuring Firestore persistence and handling conflicts when coming back online.

3. **Soft Delete Handling**: Implement filters in all queries to exclude soft-deleted items (where isDeleted = true).

4. **Transaction Safety**: Use Firestore transactions for operations that update multiple documents.

5. **Score Calculation Optimization**: Implement efficient score calculation strategies, including caching and background calculation for leaderboards.

6. **UI Feedback**: Provide clear visual feedback when tasks are completed, scores change, or items are added/removed.

This integration approach ensures that the UI accurately reflects the underlying data model structure while maintaining a clean separation of concerns and implementing the dynamic score calculation strategy.