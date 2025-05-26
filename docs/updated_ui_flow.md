# Updated UI Flow Diagram for Homeostasis App

Based on your edits to the UI design document, I've updated the UI flow diagram to reflect the new navigation structure, additional functionality, and screen interactions.

```mermaid
graph TD
    A[App Launch] --> B[Home/Task List]
    
    %% Task Management Flow
    B --> C[Add/Edit Task]
    C --> B
    B --> D[Task Completion]
    D --> B
    B -- "Swipe Task" --> E[Task Edit/Delete Options]
    E --> B
    
    %% Shopping List Flow
    B --> F[Shopping Lists]
    F --> G[Shopping List Detail]
    G --> H[Add Shopping Item]
    H --> G
    
    %% Leaderboard Flow
    B --> I[Leaderboard/Scores]
    I -- "Scroll Down" --> J[Task Completion History]
    J -- "Swipe History Item" --> K[History Edit/Delete Options]
    K --> J
    I --> L[Reset Scores]
    L --> I
    
    %% Profile Flow
    B --> M[User Profile]
    M --> N[Edit Profile]
    N --> M
    
    %% Settings Flow
    B --> O[Settings]
    O --> P[Category Management]
    P --> Q[Add/Edit Category]
    Q --> P
    O --> R[Cloud Storage Settings]
    
    %% Bottom Navigation
    B -- "Bottom Nav" --> F
    B -- "Bottom Nav" --> I
    B -- "Bottom Nav" --> M
    B -- "Bottom Nav" --> O
    F -- "Bottom Nav" --> B
    F -- "Bottom Nav" --> I
    F -- "Bottom Nav" --> M
    F -- "Bottom Nav" --> O
    I -- "Bottom Nav" --> B
    I -- "Bottom Nav" --> F
    I -- "Bottom Nav" --> M
    I -- "Bottom Nav" --> O
    M -- "Bottom Nav" --> B
    M -- "Bottom Nav" --> F
    M -- "Bottom Nav" --> I
    M -- "Bottom Nav" --> O
    O -- "Bottom Nav" --> B
    O -- "Bottom Nav" --> F
    O -- "Bottom Nav" --> I
    O -- "Bottom Nav" --> M
```

## Key Changes in the Updated Flow:

1. **Added Settings Section**:
   - New navigation item for Settings
   - Category Management moved to Settings
   - Added Cloud Storage Settings

2. **Enhanced Task Interactions**:
   - Added swipe functionality for tasks to reveal edit/delete options
   - Added date selection in task completion dialog

3. **Enhanced Leaderboard**:
   - Added scroll down to view task completion history
   - Added swipe functionality for history items to reveal edit/delete options

4. **Simplified Bottom Navigation**:
   - Updated with more concise labels (Tasks, Shop, Score/hist, Me, cog)
   - All sections accessible from any other section via bottom navigation

5. **Additional Details**:
   - Time information added to task completion history
   - Multiple shopping lists support
   - Task categorization and filtering

This updated flow diagram better represents the user journey through the application based on your UI design changes.