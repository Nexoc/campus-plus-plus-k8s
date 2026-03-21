// src/app/config/env.ts
//
// Centralized access to environment flags.
// This file defines runtime environment information
// and feature toggles used across the application.
//
// IMPORTANT:
// - This file should contain ONLY environment-related logic
// - No business logic, no feature logic
// - Keeps environment handling consistent and testable

// --------------------------------------------------
// Environment flags provided by Vite
// --------------------------------------------------
//
// import.meta.env.DEV  -> true in development mode
// import.meta.env.PROD -> true in production build
//
export const isDev = import.meta.env.DEV;
export const isProd = import.meta.env.PROD;

// --------------------------------------------------
// Logging feature toggle
// --------------------------------------------------
//
// Logging should be enabled ONLY in development.
// In production:
// - console output should be minimal
// - sensitive information must not be logged
// - performance should not be affected
//
export const enableLogs = isDev;