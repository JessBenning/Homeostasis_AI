# Score Calculation Approach for Homeostasis App

## Dynamic Score Calculation vs. Stored Score

Based on your suggestion, we'll implement a dynamic score calculation approach using the TaskHistory model rather than storing a static score in the User model. This approach offers several important benefits:

### Benefits of Dynamic Score Calculation

1. **Single Source of Truth**: 
   - The TaskHistory model becomes the single source of truth for all task completions
   - No risk of score inconsistencies between stored values and actual task history

2. **Automatic Reflection of Task Changes**:
   - If a task's point value is modified, scores will automatically reflect this change
   - No need to update stored scores when task point values change

3. **Support for Score Resets**:
   - Implementing the "reset scores" feature becomes simpler
   - Can reset scores by filtering TaskHistory entries by date rather than zeroing out stored values

4. **Flexible Scoring Rules**:
   - Can easily implement time-based scoring (e.g., tasks completed this week/month)
   - Could add bonus points for streaks or consistent task completion
   - Could implement weighted scoring based on task categories

5. **Auditability**:
   - Score calculation is transparent and can be verified by examining task history
   - Helps resolve any disputes about point totals

## Implementation Approach

### Data Model Changes

```diff
User {
    id: String
    name: String
    profileImageUrl: String
    createdAt: Timestamp
    lastActive: Timestamp
-   score: Integer  // Remove stored score field
}

TaskHistory {
    id: String
    taskId: String  // Reference to the task
    userId: String  // User who completed the task
    completedAt: Timestamp  // When the task was completed
    customCompletionDate: Date  // To support custom date selection
    pointValue: Integer  // Store the point value at time of completion
    isDeleted: Boolean
    lastModifiedAt: Timestamp
}
```

### Score Calculation Logic

```javascript
// Calculate score for a specific user
function calculateUserScore(userId, options = {}) {
  // Default options
  const defaultOptions = {
    startDate: null,  // If provided, only count tasks after this date
    endDate: null,    // If provided, only count tasks before this date
    categoryId: null  // If provided, only count tasks in this category
  };
  
  const opts = { ...defaultOptions, ...options };
  
  // Build query
  let query = firebase.firestore()
    .collection('taskHistory')
    .where('userId', '==', userId)
    .where('isDeleted', '==', false);
    
  // Add date filters if provided
  if (opts.startDate) {
    query = query.where('completedAt', '>=', opts.startDate);
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
}
```

### UI Integration

For UI components that need to display scores (Leaderboard, Profile screen), we'll calculate scores on-demand:

```javascript
// Get leaderboard data
function getLeaderboard() {
  // Get all users
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
      
      // Wait for all score calculations to complete
      return Promise.all(scorePromises);
    })
    .then(usersWithScores => {
      // Sort by score (descending)
      return usersWithScores.sort((a, b) => b.score - a.score);
    });
}
```

## Performance Considerations

While dynamic calculation offers many benefits, it does come with some performance considerations:

1. **Query Optimization**:
   - Store the point value in the TaskHistory model at the time of completion
   - This avoids having to join with the Task collection for basic score calculations
   - Consider denormalizing category information if category filtering is common

2. **Caching Strategy**:
   - Cache calculated scores in memory or local storage
   - Implement a smart invalidation strategy (e.g., invalidate when new tasks are completed)

3. **Pagination for Large Histories**:
   - For users with extensive task histories, implement pagination in the calculation
   - Consider time-based partitioning (e.g., calculate by month and sum)

4. **Background Calculation**:
   - For the leaderboard, consider calculating scores in a background process
   - Could store temporary results in a separate collection that's updated periodically

## Additional Enhancements

This approach enables several advanced features:

1. **Time-Based Leaderboards**:
   - Weekly, monthly, or yearly leaderboards by filtering the date range
   - "Most improved" calculations by comparing different time periods

2. **Category-Based Scoring**:
   - Leaderboards specific to certain categories of tasks
   - "Specialist" badges for users who excel in specific categories

3. **Scoring Rules Engine**:
   - Could implement a rules engine for complex scoring logic
   - Example: bonus points for completing all tasks in a category, or completing tasks on consecutive days

4. **Historical Analysis**:
   - Track score progression over time
   - Generate insights about productivity patterns

This dynamic calculation approach provides a more flexible, maintainable, and accurate system for tracking user scores in the Homeostasis app.