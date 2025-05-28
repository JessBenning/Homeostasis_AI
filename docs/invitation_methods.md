# Invitation Methods for Household Groups

This document outlines the pros and cons of different methods for inviting members to a household group in the Homeostasis app.

## 1. Email with Deep Link

*   **Pros:**
    *   Familiar and widely used method.
    *   Easy to integrate with existing authentication systems.
    *   Can include a personalized message and a direct link to accept the invitation.
    *   Seamlessly redirects users to the app and directly to the invitation acceptance screen.
*   **Cons:**
    *   Requires the user to have an email address.
    *   Invitations can be lost in spam folders.
    *   Requires more complex implementation (sending emails, handling bounces, etc.).
    *   Requires configuration of deep links in the app and on the Firebase console.
    *   May not work if the app is not installed on the user's device.

## 2. ID Number (Deprecated)

*   **Pros:**
    *   Simple to implement.
    *   Doesn't require any external services.
*   **Cons:**
    *   Requires the user to manually enter the ID number, which can be error-prone.
    *   Less user-friendly than other methods.
    *   Requires a mechanism to display the ID number to the inviter.

## 3. QR Code (Deprecated)

*   **Pros:**
    *   Easy to use on mobile devices.
    *   Reduces the risk of errors compared to manual entry.
    *   Can be easily shared in person or via screenshots.
*   **Cons:**
    *   Requires the user to have a QR code scanner app.
    *   May not be suitable for users who are not familiar with QR codes.
    *   Requires a library to generate and scan QR codes.

## 4. Unique Invitation Link (Alternative)

*   **Pros:**
    *   Simple to implement.
    *   Can be easily shared via various channels (e.g., messaging apps, social media).
*   **Cons:**
    *   Requires a mechanism to generate and track unique invitation links.
    *   May be vulnerable to abuse if the links are not properly secured.

## Recommendation

Based on the analysis, the recommended method for inviting members to a household group is **email with a deep link**. This method provides a familiar and user-friendly experience, while also ensuring that the user is redirected to the correct screen in the app.