# Homeostasis App - Architecture Summary

This document provides a high-level overview of the Homeostasis app architecture, highlighting key design decisions and their benefits.

## 1. Core Architecture Components

### 1.1 Client-Side Architecture
- **Native Android** with Kotlin
- **MVVM Pattern** for clean separation of concerns
- **Repository Pattern** for data access abstraction
- **Room Database** for local persistence
- **Firebase SDK** for cloud services

### 1.2 Backend Architecture
- **Firebase Authentication** for user management
- **Cloud Firestore** for data storage and synchronization
- **Firebase Storage** for profile images
- **Firebase Cloud Messaging** for notifications (optional)

## 2. Key Data Models

### 2.1 User Model
- Stores basic user information (name, profile image)
- Tracks last reset score and reset count
- Does NOT store calculated scores (dynamic calculation approach)

### 2.2 Task Model
- Represents available tasks that can be completed
- Contains task metadata (title, description, points, category)
- Does NOT store completion information (separation of concerns)

### 2.3 TaskHistory Model
- Records each instance of task completion
- Links to both task and user
- Stores point value at time of completion (for accurate historical scoring)
- Supports custom completion dates
- Includes flags for soft deletion and archiving (for threshold resets)

### 2.4 Category Model
- Organizes tasks into logical groups
- Supports filtering and organization of tasks
- Includes visual representation with colors and icons

### 2.5 ShoppingList Model
- Represents a collection of shopping items
- Supports multiple shopping lists

### 2.6 ShoppingItem Model
- Represents base item definitions in a global catalog
- Tracks usage statistics across all shopping lists
- Enables intelligent suggestions for quick-add

### 2.7 ShoppingListItem Model
- Maps items to specific shopping lists
- Stores list-specific properties (quantity, notes, checked status)
- Enables the same item to appear in multiple lists

### 2.8 Settings Model
- Stores application-wide settings
- Contains threshold configuration for resets
- Tracks reset history information

### 2.9 ResetHistory Model
- Records each threshold reset event
- Stores user scores before and after reset
- Provides historical reference for past resets

## 3. Key Design Decisions

### 3.1 Separation of Task and TaskHistory
**Decision**: Separate task definitions from task completion records.

**Benefits**:
- Clear separation of concerns
- Simplified data model
- Support for task history and analytics
- Easier implementation of task reuse

### 3.2 Dynamic Score Calculation
**Decision**: Calculate scores dynamically from TaskHistory rather than storing in User model.

**Benefits**:
- Single source of truth for scores
- Automatic reflection of task point changes
- Support for time-based leaderboards
- Flexible scoring rules
- Better auditability

### 3.3 Shared Items with List Mapping
**Decision**: Separate item definitions from their presence in shopping lists.

**Benefits**:
- No duplication of item definitions
- Shared history and usage statistics across lists
- Better quick-add suggestions based on global usage
- Support for item-specific properties in each list

### 3.4 Soft Delete Approach
**Decision**: Use isDeleted flag rather than actually removing records.

**Benefits**:
- Support for undo functionality
- Preservation of historical data
- Simplified implementation of data recovery
- Better analytics capabilities

### 3.5 Point Value Storage in TaskHistory
**Decision**: Store the task's point value at the time of completion in the TaskHistory record.

**Benefits**:
- Historical accuracy if task point values change
- Reduced need for joins when calculating scores
- Improved query performance for leaderboards

### 3.6 Threshold Reset Feature
**Decision**: Implement automatic reset of task history when all users reach a threshold score.

**Benefits**:
- Prevents database bloat from excessive task history
- Creates "seasons" or cycles to maintain competition
- Provides flexible reset behaviors (zero, relative, percentage)
- Maintains historical reference through reset history

## 4. Data Flow Patterns

### 4.1 Task Completion Flow
1. User selects a task to complete
2. App shows completion dialog with date picker
3. User confirms completion
4. App creates new TaskHistory entry with current task point value
5. App checks if threshold has been reached
6. UI updates to reflect completion and possible reset

### 4.2 Score Calculation Flow
1. App needs to display scores (e.g., leaderboard, profile)
2. For each user, query TaskHistory entries (filtering by reset if needed)
3. Sum pointValue fields for non-deleted, non-archived entries
4. Sort and display results
5. (Optional) Cache results for performance

### 4.3 Shopping List Flow
1. User creates or selects a shopping list
2. User adds items to the list (from global catalog or as new items)
3. New items are added to both the global catalog and the specific list
4. When checked, items remain in the list but marked as completed
5. Usage statistics are updated in the global catalog

### 4.4 Threshold Reset Flow
1. After task completion, app checks if all users have reached threshold
2. If threshold reached, app initiates reset process
3. Task history entries are archived (marked with isArchived flag)
4. User scores are adjusted according to reset behavior
5. Reset history entry is created
6. UI notifies users of the reset

## 5. Performance Considerations

### 5.1 Score Calculation Optimization
- Implement caching for calculated scores
- Consider background calculation for leaderboards
- Use pagination for large task histories
- Filter by reset periods for efficient queries

### 5.2 Shopping List Optimization
- Denormalize item names into ShoppingListItem for faster list rendering
- Use composite indexes for efficient queries across ShoppingListItem and ShoppingItem
- Implement caching for frequently accessed shopping lists and items

### 5.3 Offline Support
- Configure Firestore for offline persistence
- Implement conflict resolution for offline changes
- Provide clear UI indicators for offline status
- Handle resets that occur while device is offline

### 5.4 Query Optimization
- Use appropriate Firestore indexes
- Consider denormalization for frequent queries
- Implement pagination for large result sets
- Use composite queries for filtering by multiple conditions

## 6. Security Considerations

### 6.1 Firestore Security Rules
- Ensure users can only read/write appropriate data
- Implement validation rules for data integrity
- Protect sensitive user information
- Secure reset operations to prevent unauthorized resets

### 6.2 Authentication
- Use Firebase Authentication for secure user management
- Implement appropriate session management
- Consider anonymous authentication for quick onboarding

## 7. Future Extensibility

The architecture has been designed to support future enhancements:

### 7.1 Advanced Scoring
- Time-based scoring (weekly, monthly leaderboards)
- Category-specific leaderboards
- Streak bonuses or multipliers

### 7.2 Enhanced Task Management
- Recurring tasks
- Task dependencies
- Task priorities

### 7.3 Advanced Shopping Features
- Item categorization
- Price tracking
- Automatic list suggestions based on consumption patterns
- Integration with online grocery services

### 7.4 Advanced Reset Features
- Individual thresholds based on user capability
- Time-based automatic resets
- Category-specific resets

This architecture provides a solid foundation for the Homeostasis app while maintaining flexibility for future growth and feature additions.