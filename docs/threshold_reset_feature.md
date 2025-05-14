# Task History Threshold Reset Feature

## Overview

The threshold reset feature automatically clears task history when all family members reach a specified score threshold. This prevents excessive accumulation of task history records while maintaining the competitive aspect of the app.

## Key Concepts

1. **Threshold Value**: A configurable score that triggers the reset when all users reach it
2. **Reset Process**: Clears task history while preserving relative user standings
3. **Score Preservation**: Options for handling user scores after reset
4. **Reset History**: Tracking of past resets for reference

## Detailed Design

### 1. Data Model Updates

#### 1.1 Settings Model Addition

```diff
Settings {
    id: String  // Usually a single document with a fixed ID like "app_settings"
+   scoreThreshold: {
+       value: Integer  // The threshold score that triggers a reset
+       resetBehavior: String  // "zero", "relative", or "percentage"
+       preserveScoreDifference: Boolean  // Whether to maintain score differences after reset
+   }
    cloudSync: {
        enabled: Boolean
        lastSyncTime: Timestamp
        syncFrequency: String
    }
    resetHistory: {
        lastResetTime: Timestamp
        resetBy: String (User ID)
+       resetCount: Integer  // Number of times reset has occurred
    }
}
```

#### 1.2 New ResetHistory Model

```
ResetHistory {
    id: String
    resetTime: Timestamp
    triggeringUser: String (User ID)  // Last user to reach threshold
    thresholdValue: Integer  // Threshold value at time of reset
    userScores: [  // Snapshot of user scores at reset time
        {
            userId: String,
            scoreBefore: Integer,
            scoreAfter: Integer
        }
    ]
}
```

#### 1.3 User Model Addition

```diff
User {
    id: String
    name: String
    profileImageUrl: String
    createdAt: Timestamp
    lastActive: Timestamp
+   lastResetScore: Integer  // Score at the time of last reset
+   resetCount: Integer  // Number of resets this user has participated in
}
```

### 2. Reset Behaviors

#### 2.1 Zero Reset
All users' effective scores return to zero after reset, but historical data is preserved for reference.

#### 2.2 Relative Reset
All users' scores are reduced by the threshold value, maintaining their relative positions.

#### 2.3 Percentage Reset
All users keep a percentage of their scores above the threshold (e.g., 10% of excess).

### 3. Implementation Approach

#### 3.1 Threshold Monitoring

```javascript
// Check if threshold has been reached after each task completion
function checkThresholdReached() {
  // Get the threshold value from settings
  return firebase.firestore()
    .collection('settings')
    .doc('app_settings')
    .get()
    .then(doc => {
      const settings = doc.data();
      const threshold = settings.scoreThreshold.value;
      
      // Get all users and their scores
      return firebase.firestore()
        .collection('users')
        .get()
        .then(snapshot => {
          const users = [];
          snapshot.forEach(doc => {
            users.push({
              id: doc.id,
              ...doc.data()
            });
          });
          
          // Calculate score for each user
          const scorePromises = users.map(user => 
            calculateUserScore(user.id).then(score => ({
              ...user,
              score
            }))
          );
          
          return Promise.all(scorePromises);
        })
        .then(usersWithScores => {
          // Check if all users have reached the threshold
          const allReachedThreshold = usersWithScores.every(user => user.score >= threshold);
          
          if (allReachedThreshold) {
            return performReset(usersWithScores, threshold, settings.scoreThreshold.resetBehavior);
          }
          
          return false; // Threshold not reached
        });
    });
}
```

#### 3.2 Reset Process

```javascript
// Perform the reset when threshold is reached
function performReset(usersWithScores, threshold, resetBehavior) {
  // Start a Firestore batch operation
  const batch = firebase.firestore().batch();
  
  // Create a reset history record
  const resetHistoryRef = firebase.firestore().collection('resetHistory').doc();
  const resetTime = firebase.firestore.FieldValue.serverTimestamp();
  
  // Determine the triggering user (last to reach threshold)
  // This would be the user who just completed a task
  const triggeringUser = firebase.auth().currentUser.uid;
  
  // Prepare user scores for history and update
  const userScores = [];
  
  usersWithScores.forEach(user => {
    let scoreAfter = 0;
    
    // Apply different reset behaviors
    if (resetBehavior === 'relative') {
      scoreAfter = Math.max(0, user.score - threshold);
    } else if (resetBehavior === 'percentage') {
      const excess = user.score - threshold;
      scoreAfter = Math.floor(excess * 0.1); // Keep 10% of excess
    }
    // 'zero' behavior defaults to scoreAfter = 0
    
    userScores.push({
      userId: user.id,
      scoreBefore: user.score,
      scoreAfter: scoreAfter
    });
    
    // Update user document
    const userRef = firebase.firestore().collection('users').doc(user.id);
    batch.update(userRef, {
      lastResetScore: user.score,
      resetCount: firebase.firestore.FieldValue.increment(1)
    });
  });
  
  // Add reset history record
  batch.set(resetHistoryRef, {
    resetTime: resetTime,
    triggeringUser: triggeringUser,
    thresholdValue: threshold,
    userScores: userScores
  });
  
  // Update app settings
  const settingsRef = firebase.firestore().collection('settings').doc('app_settings');
  batch.update(settingsRef, {
    'resetHistory.lastResetTime': resetTime,
    'resetHistory.resetBy': triggeringUser,
    'resetHistory.resetCount': firebase.firestore.FieldValue.increment(1)
  });
  
  // Archive task history
  // Instead of deleting, we'll mark all existing history as "archived"
  return firebase.firestore()
    .collection('taskHistory')
    .where('isDeleted', '==', false)
    .get()
    .then(snapshot => {
      snapshot.forEach(doc => {
        batch.update(doc.ref, {
          isArchived: true,
          archivedInResetId: resetHistoryRef.id
        });
      });
      
      // Commit all the changes
      return batch.commit();
    })
    .then(() => {
      return true; // Reset successful
    });
}
```

#### 3.3 Score Calculation with Reset Awareness

```javascript
// Calculate user score with reset awareness
function calculateUserScore(userId, options = {}) {
  // Default options
  const defaultOptions = {
    includeArchived: false,  // Whether to include archived history
    sinceLastReset: true,    // Only count since last reset
    startDate: null,         // If provided, only count tasks after this date
    endDate: null,           // If provided, only count tasks before this date
    categoryId: null         // If provided, only count tasks in this category
  };
  
  const opts = { ...defaultOptions, ...options };
  
  // Build query
  let query = firebase.firestore()
    .collection('taskHistory')
    .where('userId', '==', userId)
    .where('isDeleted', '==', false);
    
  // Filter out archived entries if not included
  if (!opts.includeArchived) {
    query = query.where('isArchived', '==', false);
  }
  
  // If sinceLastReset, get the last reset time
  let lastResetPromise = Promise.resolve(null);
  if (opts.sinceLastReset) {
    lastResetPromise = firebase.firestore()
      .collection('settings')
      .doc('app_settings')
      .get()
      .then(doc => {
        const settings = doc.data();
        return settings.resetHistory.lastResetTime;
      });
  }
  
  return lastResetPromise.then(lastResetTime => {
    // Add date filters if provided
    if (opts.startDate) {
      query = query.where('completedAt', '>=', opts.startDate);
    } else if (lastResetTime) {
      query = query.where('completedAt', '>=', lastResetTime);
    }
    
    if (opts.endDate) {
      query = query.where('completedAt', '<=', opts.endDate);
    }
    
    // Execute query
    return query.get().then(snapshot => {
      let totalScore = 0;
      
      // Process results
      snapshot.forEach(doc => {
        const historyEntry = doc.data();
        
        // If category filter is applied, we need to get the task details
        if (opts.categoryId) {
          // This would be more efficient with a denormalized categoryId in the TaskHistory model
          return firebase.firestore()
            .collection('tasks')
            .doc(historyEntry.taskId)
            .get()
            .then(taskDoc => {
              const task = taskDoc.data();
              if (task.categoryId === opts.categoryId) {
                totalScore += historyEntry.pointValue;
              }
            });
        } else {
          // No category filter, simply add points
          totalScore += historyEntry.pointValue;
        }
      });
      
      return totalScore;
    });
  });
}
```

### 4. UI Components

#### 4.1 Settings Screen Addition

```
┌─────────────────────────────────────┐
│ Score Threshold Settings            │
├─────────────────────────────────────┤
│                                     │
│ Threshold Value: [_____]            │
│                                     │
│ Reset Behavior:                     │
│ ○ Zero (All scores return to zero)  │
│ ● Relative (Subtract threshold)     │
│ ○ Percentage (Keep 10% of excess)   │
│                                     │
│ [ ] Preserve score differences      │
│                                     │
│ Current Progress:                   │
│ Mom: 85/100                         │
│ Dad: 70/100                         │
│ Child: 45/100                       │
│                                     │
│ [SAVE]                              │
└─────────────────────────────────────┘
```

#### 4.2 Reset History Screen

```
┌─────────────────────────────────────┐
│ Reset History                       │
├─────────────────────────────────────┤
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ Reset #3 - May 15, 2025         │ │
│ │ Triggered by: Mom               │ │
│ │ Threshold: 100 points           │ │
│ │                                 │ │
│ │ Mom: 120 → 20 points            │ │
│ │ Dad: 105 → 5 points             │ │
│ │ Child: 100 → 0 points           │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ Reset #2 - April 10, 2025       │ │
│ │ Triggered by: Dad               │ │
│ │ Threshold: 75 points            │ │
│ │                                 │ │
│ │ Mom: 80 → 5 points              │ │
│ │ Dad: 75 → 0 points              │ │
│ │ Child: 75 → 0 points            │ │
│ └─────────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
```

#### 4.3 Reset Notification

```
┌─────────────────────────────────────┐
│         Threshold Reached!          │
│                                     │
│ All family members have reached     │
│ the 100 point threshold!            │
│                                     │
│ The task history has been reset.    │
│                                     │
│ New scores:                         │
│ Mom: 20 points                      │
│ Dad: 5 points                       │
│ Child: 0 points                     │
│                                     │
│             [OK]                    │
└─────────────────────────────────────┘
```

### 5. Benefits and Considerations

#### 5.1 Benefits
- **Prevents Database Bloat**: Keeps the task history collection from growing indefinitely
- **Maintains Competition**: Provides regular "seasons" or cycles to the competition
- **Flexible Options**: Different reset behaviors accommodate different family preferences
- **Historical Reference**: Maintains a record of past resets and achievements

#### 5.2 Implementation Considerations
- **Transaction Safety**: Reset operation must be atomic to prevent data inconsistencies
- **UI Feedback**: Clear notification when threshold is reached and reset occurs
- **Offline Handling**: Proper handling of resets that occur while a device is offline
- **Performance**: Efficient querying of task history with reset awareness

#### 5.3 Alternative Approaches

**1. Archiving Instead of Marking**
Instead of adding an `isArchived` flag, move archived history to a separate collection:

```javascript
// During reset, move task history to archive collection
snapshot.forEach(doc => {
  const historyData = doc.data();
  batch.set(
    firebase.firestore().collection('archivedTaskHistory').doc(doc.id),
    {
      ...historyData,
      archivedInResetId: resetHistoryRef.id
    }
  );
  batch.delete(doc.ref);
});
```

**2. Time-Based Resets**
Instead of threshold-based resets, implement automatic resets based on time periods (weekly, monthly, etc.).

**3. Individual Thresholds**
Allow different threshold values for different family members based on age or capability.

### 6. Conclusion

The threshold reset feature provides an elegant solution to manage task history growth while maintaining the competitive and motivational aspects of the app. By preserving historical data and offering flexible reset behaviors, it ensures that the app remains engaging over long periods of use.