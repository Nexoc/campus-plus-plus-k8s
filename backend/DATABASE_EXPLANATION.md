# Database Explanation — Campus++ Backend

## Purpose

This document explains the current PostgreSQL schema used by the Campus++
backend.

It reflects the Flyway migrations that exist today, not an early design draft.

## Database Role

The backend stores its domain data in PostgreSQL on the dedicated DB host:

- host: `192.168.56.20`
- port: `5432`
- database: `campus`
- schema: `app`

The backend runs Flyway on startup and treats the migration set as the schema
source of truth.

## Core Principles

- PostgreSQL is the relational system of record
- Flyway migrations define the schema contract
- backend data lives in schema `app`
- UUIDs are used for identifiers
- user identity comes from the external auth domain
- no foreign keys point to auth-service-owned user tables

## Current Migration Set

Current migration chain:

- `V1__init_schema_clean.sql`
- `V2__study_programs.sql`
- `V3__courses.sql`
- `V4__reviews.sql`
- `V5__favourites.sql`
- `V6__threads.sql`
- `V7__posts.sql`
- `V8__comments.sql`
- `V9__reports.sql`
- `V10__reactions.sql`
- `V11__watch_subscriptions.sql`

## Current Tables By Area

Study program and course catalog:

- `study_programs`
- `modules`
- `courses`
- `study_program_courses`

User bookmarks and content:

- `study_program_favourites`
- `favourites`
- `reviews`
- `threads`
- `posts`
- `comments`
- `course_materials`

Moderation and engagement:

- `reports`
- `reactions`
- `watch_subscriptions`

Reserved / partially implemented:

- `course_suggestions`

## Important Design Notes

### Study Programs

`study_programs` stores imported HCW study program metadata.

`modules` stores module-level information per study program.

`study_program_favourites` stores user bookmarks for study programs.

### Courses

`courses` stores imported course metadata, including structured HTML/JSON
content from the importer pipeline.

`study_program_courses` models the many-to-many relation between programs and
courses.

`course_suggestions` exists in the schema, but there is currently no active API
module using it.

`course_materials` stores file metadata only. File bytes are stored on disk by
the backend under `/data/course-materials`.

### Reviews

`reviews` stores user reviews for courses, including moderation flags and
rating-related fields.

### Favourites

There are two favourites concepts in the current schema:

- `favourites` for course favourites
- `study_program_favourites` for study program favourites

### Discussion

Discussion is split across:

- `threads`
- `posts`
- `comments`

This keeps thread containers, post bodies, and comment bodies separate.

### Moderation

`reports` stores user-generated moderation reports with polymorphic targets.

Important columns:

- `target_type`
- `target_id`
- `status`
- `comment`

### Engagement

`reactions` stores simple reactions against supported target types.

`watch_subscriptions` stores watch/subscription state for supported target
types.

## User Identity Strategy

The backend does not own a local users table.

Instead:

- auth/account data is managed by the separate auth service
- backend tables keep `user_id` references as UUIDs
- user authorization is enforced in application logic using trusted request
  context

This is intentional and matches the service boundary.

## Foreign Key Strategy

Foreign keys are used for internal domain integrity only.

Examples:

- `modules.study_program_id -> study_programs.id`
- `courses.module_id -> modules.id`
- `study_program_courses.* -> study_programs / courses`
- `reviews.course_id -> courses.id`
- `threads.course_id -> courses.id`
- `posts.thread_id -> threads.id`
- `comments.post_id -> posts.id`
- `course_materials.course_id -> courses.id`

There are no foreign keys to external auth-owned user records.

## Index Strategy

The schema uses ordinary and GIN indexes where needed.

Notable current patterns:

- search/filter indexes on catalog fields
- GIN indexes on JSONB content blocks
- lookup indexes on target-based moderation and engagement tables
- time-based indexes for ordered content retrieval

## Current Implementation Status

This schema is actively used by the running backend.

Confirmed from the current codebase:

- Flyway migrates the schema on startup
- importer populates study programs, modules, and courses
- backend APIs use reviews, favourites, threads, posts, comments, reports,
  reactions, watch subscriptions, and course materials

The schema is no longer only a design draft.
