# 3. Functional Requirements (MoSCoW, Rupp)

## Status Note

This file is a requirements snapshot, not the runtime/deployment source of
truth.

For the current deployed DEV setup as of April 20, 2026, use:

- `README.md`
- `deploy/README.md`
- `deploy/docs/*`

All functional requirements below follow the MoSCoW prioritization and are formulated using a Rupp-style structure (“The system shall…”).

## 3.1 Must Requirements

**FR-M-1 (Must)**

The system shall allow users to view a list of at least one HCW study program on the homepage.

**FR-M-2 (Must)**

The system shall allow users to view a list of at least five HCW courses across one or more study programs.

**FR-M-3 (Must)**

The system shall display a course detail page showing at least: course title, short description, study program and semester.

**FR-M-4 (Must)**

The system shall display a study program detail page showing at least: program title, short description, degree type and list of associated courses.

**FR-M-5 (Must)**

The system shall allow a user to register a new account using email (or HCW ID) and password.

**FR-M-6 (Must)**

The system shall allow a registered user to log in using email (or HCW ID) and password and start an authenticated session.

**FR-M-7 (Must)**

The system shall support the roles Student, Applicant and Moderator.

**FR-M-8 (Must)**

The system shall enforce role-based access such that only Students and Moderators can create or edit reviews and only Moderators can access moderation views.

**FR-M-9 (Must)**

The system shall allow logged-in users with role Student or Moderator to create a course review with a rating from 1 to 5 and optional text.

**FR-M-10 (Must)**

The system shall store submitted reviews persistently and link each review to a course and its author.

**FR-M-11 (Must)**

The system shall display all existing reviews and the average rating on the corresponding course detail page to all users, including users who are not logged in.

**FR-M-12 (Must)**

The system shall allow logged-in users to add courses and study programs to their favourites list.

**FR-M-13 (Must)**

The system shall allow logged-in users to remove items from their favourites list.

**FR-M-14 (Must)**

The system shall provide a view where a logged-in user can see their favourites list.

**FR-M-15 (Must)**

The system shall allow logged-in users to report a review as inappropriate by selecting a reason from a predefined list and optionally entering a comment.

**FR-M-16 (Must)**

The system shall provide a moderator view that lists all open reports.

**FR-M-17 (Must)**

The system shall allow Moderators to change the status of a reported review to at least “keep visible”, “edit” or “delete”.

## 3.2 Should Requirements

**FR-S-1 (Should)**

The system should allow users to filter courses by study program and at least one additional attribute (for example semester or difficulty).

**FR-S-2 (Should)**

The system should allow users to sort reviews on a course page by newest first, oldest first, highest rating and lowest rating.

**FR-S-3 (Should)**

The system should display a simple campus map showing at least one HCW building relevant for the selected study program.

**FR-S-4 (Should)**

The system should display the current number of open reports in the moderator navigation (for example as a badge with a number).

## 3.3 Could Requirements

**FR-C-1 (Could)**

The system could allow users with role Student or Moderator to upload files (for example PDFs or images) as course materials with a maximum file size.

**FR-C-2 (Could)**

The system could provide discussion threads for each course where users can create posts and comments.

**FR-C-3 (Could)**

The system could allow users to react to posts or reviews with simple reactions (for example a thumbs-up icon).

**FR-C-4 (Could)**

The system could send e-mail notifications to users who opted in when there is new activity in a watched course or thread.

## 3.4 Won’t

**FR-W-1 (Won’t)**

The system will not provide video calls or real-time chat.

**FR-W-2 (Won’t)**

The system will not provide dating or “Campus Tinder” functionality.

**FR-W-3 (Won’t)**

The system will not include official timetables, grades or calendars that are already covered by Campus+.

**FR-W-4 (Won’t)**

The system will not integrate external exam-registration systems.

**FR-W-5 (Won’t)**

The system will not provide advanced analytics dashboards.

# 4. Non-functional Requirements

All non-functional requirements are formulated to allow objective verification.

## **4.1 Performance**

- **NFR-1** For 95% of requests to course and study program pages under normal load, the server response time shall not exceed 2 seconds.

## **4.2 Security**

- **NFR-2** Passwords stored using secure hashing.
- **NFR-3** Only authenticated users can write content.
- **NFR-4** All communication over HTTPS.

## **4.3 Reliability**

- **NFR-5** Availability ≥ 99% per month.
- **NFR-6** Automated daily backups.

## **4.4 Usability**

- **NFR-7** Clean UI accessible without training.
- **NFR-8** Main functions reachable within three clicks.
