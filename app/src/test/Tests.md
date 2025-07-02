# UAT test list

## Authentication

### [P/F: TST-000] TEMPLATE Test Name
#### Pre
* Prestep
#### Test Step and expected Result
* [P/F]: Test step -> Expected Results

### [P/F: AUTH-001] New User Registration and Onboarding
#### Pre
* Clear app cache on the test device.
* Ensure the test user email does not exist in Firebase Authentication.
* Ensure the test user email/ID does not exist in the user table in Firestore.
#### Test Step and expected Result
* [P/F]: Start app -> Login screen is presented. All other navigation options are inaccessible.
* [P/F]: Attempt login with test user email and a password -> System displays "User unknown" (or similar) message. Login fails.
* [P/F]: User taps on "Register" (or similar) link/button -> Registration screen/dialog is presented.
* [P/F]: User enters required registration details (e.g., email, password, name) and submits -> System displays "User created" (or similar) message. User is automatically logged in. Onboarding dialog ("Join or Create Group") is presented.

### [P/F: AUTH-002] Login Existing User without Group
#### Pre
* Test user exists in Firebase Authentication.
* Test user exists in Firestore user table.
* Test user in Firestore has their `householdGroupId` field set to null, empty, or a default non-group ID.
#### Test Step and expected Result
* [P/F]: User starts app and navigates to the login screen (if not already there). -> Login screen is presented.
* [P/F]: User enters credentials for the existing user without a group and attempts login -> Login is successful. Onboarding dialog ("Join or Create Group") is presented.

### [P/F: AUTH-003] Login Existing User with Group
#### Pre
* Test user exists in Firebase Authentication.
* Test user exists in Firestore user table.
* Test user in Firestore has a valid `householdGroupId` field.
#### Test Step and expected Result
* [P/F]: User starts app and navigates to the login screen (if not already there). -> Login screen is presented.
* [P/F]: User enters credentials for the existing user with a group and attempts login -> Login is successful. User is presented with the main tasks screen for their group, group name is part of the title.

### [P/F: AUTH-004] Onboarding - Cancel Onboarding
#### Pre
* Test user exists in Firebase Authentication.
* Test user exists in Firestore user table.
* Test user in Firestore has their `householdGroupId` field set to null, empty, or a default non-group ID.
* User has just logged in or registered, and the onboarding dialog ("Join or Create Group") is presented.
#### Test Step and expected Result
* [P/F]: User is presented with the onboarding dialog ("Join or Create Group"). -> Dialog is visible with options to join or create.
* [P/F]: User taps "Cancel" or closes the onboarding dialog -> User is navigated back to the login screen. User remains logged out or in a state requiring group association.

### [P/F: AUTH-005] Onboarding - Join Existing Group
#### Pre
* Test user exists in Firebase Authentication.
* Test user exists in Firestore user table.
* Test user in Firestore has their `householdGroupId` field set to null, empty, or a default non-group ID.
* An existing group with a valid, shareable group invite code/ID exists.
* User has just logged in or registered, and the onboarding dialog ("Join or Create Group") is presented.
#### Test Step and expected Result
* [P/F]: User is presented with the onboarding dialog ("Join or Create Group"). -> Dialog is visible with options to join or create or cancel/logout.
* [P/F]: User taps "Join Group" button -> "Join Group" dialog/screen is presented, prompting for an invite code/ID.
* [P/F]: User enters/pastes a valid group invite code/ID and submits -> System displays a success message (e.g., snackbar "Successfully joined group!"). User is navigated to the main tasks screen for the joined group. Group name is part of the title.
* [P/F]: Or User cancels -> User shown onboarding dialog.

### [P/F: AUTH-006] Onboarding - Create New Group
#### Pre
* Test user exists in Firebase Authentication.
* Test user exists in Firestore user table.
* Test user in Firestore has their `householdGroupId` field set to null, empty, or a default non-group ID.
* User has just logged in or registered, and the onboarding dialog ("Join or Create Group") is presented.
#### Test Step and expected Result
* [P/F]: User is presented with the onboarding dialog ("Join or Create Group"). -> Dialog is visible with options to join or create.
* [P/F]: User taps "Create Group" button -> "Create Group" dialog/screen is presented, prompting for a new group name.
* [P/F]: User enters a valid new group name and submits -> System displays a success message (e.g., snackbar "Group created successfully!"). User is navigated to the main tasks screen for the newly created group. Group name is part of the title.
* [P/F]: Or User cancels -> User shown onboarding dialog.
---

## Task Management

### [P/F: TMA-001] Add New Task
#### Pre
* User is logged in.
* User is associated with a household group.
* User is on the main tasks screen.
#### Test Step and expected Result
* [P/F]: User taps the "Add Task" button (e.g., FAB). -> "Add Task" dialog/screen is presented with fields for title, points.
* [P/F]: User enters a task title (e.g., "Wash Dishes"). -> Title field updates correctly.
* [P/F]: User saves the new task. -> Task is added to the task list on the main screen. A success message (e.g., snackbar "Task added") is shown. Task appears for other group members.

### [P/F: TMA-002] Complete Task
#### Pre
* User is logged in and part of a group.
* At least one incomplete task exists in the group's task list.
* User is on the main tasks screen.
#### Test Step and expected Result
* [P/F]: User locates an incomplete task in the list. -> Task is visible with its details (title, points, last completed status).
* [P/F]: User taps the "Complete" button/checkbox or performs a swipe action for completion. -> Task is marked as complete (e.g., visual change, moved to a "completed" section, or "Last done by" updates). Points (if any) are visually updated for the user (in scores screen). Task completion is reflected for other group members.
* [P/F]: (If applicable) User's session completion count for this task increments. -> Session count badge/indicator updates.

### [P/F: TMA-002.1] Complete Task with date
#### Pre
* User is logged in and part of a group.
* At least one incomplete task exists in the group's task list.
* User is on the main tasks screen.
#### Test Step and expected Result
* [P/F]: User locates an incomplete task in the list -> Task is visible with its details (title, points, last completed status).
* [P/F]: User taps the "Completed date" text -> User presented with date picker.
* [P/F]: User selects a date -> Task is marked as complete (e.g., visual change, moved to a "completed" section, or "Last done by" updates). Points (if any) are visually updated for the user (in scores screen). Task completion is reflected for other group members.
* [P/F]: (If applicable) User's session completion count for this task increments. -> Session count badge/indicator updates.

### [P/F: TMA-003] Undo Task Completion (If Applicable)
#### Pre
* User is logged in and part of a group.
* User has recently completed a task.
* An option to "undo" is available (e.g., via left swipe).
#### Test Step and expected Result
* [P/F]: User triggers the "undo" action for a recently completed task. -> The task reverts to its incomplete state. Badge decrements or disappears if at 0, "Last done by" information reverts or is cleared. User's score (if affected) is adjusted back. Change is reflected for other group members.

### [P/F: TMA-004] Edit Existing Task
#### Pre
* User is logged in and part of a group.
* At least one task exists.
* User is on the main tasks screen.
* User has permissions to edit tasks (if applicable, e.g., task creator or admin).
#### Test Step and expected Result
* [P/F]: User initiates "edit" for an existing task (e.g., overflow icon then menu option). -> "Edit Task" dialog/screen is presented, pre-filled with the task's current details.
* [P/F]: User modifies the task's title (e.g., "Wash Dishes" to "Wash All Dishes"). -> Title field updates correctly.
* [P/F]: User saves the changes. -> Task in the list updates with the new title. Change is reflected for other group members and task history. A success message (e.g., snackbar "Task updated") is shown.

### [P/F: TMA-005] Delete Task
#### Pre
* User is logged in and part of a group.
* At least one task exists.
* User is on the main tasks screen.
* User has permissions to delete tasks (if applicable).
#### Test Step and expected Result
* [P/F]: User initiates "delete" for an existing task (e.g., overflow button then menu option). -> A confirmation dialog for deletion is presented (e.g., "Are you sure you want to delete 'Task Name'?").
* [P/F]: User confirms deletion. -> Task is removed from the task list. Change is reflected for other group members (task history????). A success message (e.g., snackbar "Task deleted") is shown.
* [P/F]: User cancels deletion from the confirmation dialog. -> Task remains in the task list. No changes occur.

---

## Profile Settings

### [P/F: PRF-001] View Profile Information
#### Pre
* User is logged in.
* User navigates to the "Profile Settings" screen.
#### Test Step and expected Result
* [P/F]: User lands on the profile screen. -> User's current display name, email(TODO), and profile picture (if any) are correctly displayed.

### [P/F: PRF-002] Update Display Name
#### Pre
* User is logged in.
* User is on the "Profile Settings" screen.
#### Test Step and expected Result
* [P/F]: User taps on an the name field. -> Name field becomes editable.
* [P/F]: User enters a new display name and saves. -> Display name updates on the profile screen. The new name is reflected in other parts of the app where the user's name is shown (e.g., "Last done by" on tasks, scores, etc.).

### [P/F: PRF-003] Change Profile Picture
#### Pre
* User is logged in.
* User is on the "Profile Settings" screen.
#### Test Step and expected Result
* [P/F]: User taps on the profile picture or "Change Picture" button. -> Device's image gallery/picker opens.
* [P/F]: User selects a new image from the gallery. -> The selected image is previewed on the profile settings screen.
* [P/F]: User confirms/saves the new profile picture. -> Profile picture updates on the profile screen. The new picture is reflected elsewhere (e.g., task history, scores). A success message is shown.
* [P/F]: User navigates back to the settings using the arrow button -> Settings screen is shown.
---

### [P/F: TSK-001] Settings - Reset scores and History
#### Pre
* User is logged in.
* User is on the "Settings" screen.
#### Test Step and expected Result
* [P/F]: Click Reset scores and History -> Confirmation dialog appears
* [P/F]: Click Reset -> Task history and scores cleared
* [P/F]: Click Cancel -> back to settings

## Group Management (New UAT Tests)

### [P/F: GRP-001] View Group Members (TODO)
#### Pre
* User is logged in and part of a group.
* User navigates to a "Group Settings" or "View Members" screen.
#### Test Step and expected Result
* [P/F]: User accesses the group members list. -> A list of all members currently in the user's household group is displayed, showing their names and possibly profile pictures.

### [P/F: GRP-002] Invite New Member to Group
#### Pre
* User is logged in and part of a group.
* User is on a "Group Settings" screen or has access to an "Invite Member" option.
#### Test Step and expected Result
* [P/F]: User taps "Invite Member" (or similar). -> A unique group invite code/ID is displayed. An option to share this code (e.g., via system share dialog) is available.
* [P/F]: User shares the code with another person (simulated). -> The sharing mechanism (e.g., copy to clipboard, send via messaging app) works as expected.
* [P/F]: Or User cancels -> Settings screen presented.

### [P/F: GRP-003] Join Group (Accept invite)
#### Pre
* User is logged in and part of a group with at least one other member (to avoid orphaning a group, depending on app logic).
* User is on a "Group Settings" screen.
#### Test Step and expected Result
* [P/F]: User taps "Accept Invite" button. -> A dialog is presented to allow pasting.
* [P/F]: User pressed invite button -> User is shown success notification. User is navigated to settings screen. Task and history titles indicate new group name.
* [P/F]: Or User cancels -> Settings screen presented.

### [P/F: GRP-004] Leave Group (TODO)
#### Pre
* User is logged in and part of a group with at least one other member (to avoid orphaning a group, depending on app logic).
* User is on a "Group Settings" screen.
#### Test Step and expected Result
* [P/F]: User taps "Leave Group" button. -> A confirmation dialog is presented (e.g., "Are you sure you want to leave this group?").
* [P/F]: User confirms leaving the group. -> User is removed from the group. User is navigated to the onboarding screen ("Join or Create Group") or login screen. The user no longer sees tasks from the previous group. Other group members no longer see this user in the member list.

---