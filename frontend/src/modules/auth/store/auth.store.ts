// src/modules/auth/store/auth.store.ts
//
// Auth Store (Pinia)
//
// Responsibilities:
// --------------------------------------------------
// - Store JWT token (stateless authentication)
// - Load and store current user profile from backend
// - Expose authentication state to the UI
// - Expose role-based helpers (isAdmin)
//
// Design decisions:
// --------------------------------------------------
// - JWT is treated as an opaque token (NOT parsed on frontend)
// - User profile and roles are loaded via /auth/me
// - Backend is the single source of truth for authorization
// - LocalStorage is used for token persistence
//

import http from '@/app/api/http'
import * as authApi from '@/modules/auth/api/auth.api'
import { isModeratorRole } from '@/shared/utils/roles'
import { logger } from '@/shared/utils/logger'
import { defineStore } from 'pinia'

// --------------------------------------------------
// Types
// --------------------------------------------------

interface User {
  id: string
  email: string
  nickname: string | null
  role: string
}

interface AuthState {
  token: string | null
  user: User | null
}

// --------------------------------------------------
// Store
// --------------------------------------------------

export const useAuthStore = defineStore('auth', {
  // --------------------------------------------------
  // State
  // --------------------------------------------------
  state: (): AuthState => ({
    // JWT token is restored from localStorage on page reload
    token: localStorage.getItem('token'),

    // User profile is loaded AFTER successful login
    // or restored via init() on app startup
    user: null,
  }),

  // --------------------------------------------------
  // Getters
  // --------------------------------------------------
  getters: {
    /**
     * Indicates whether user is authenticated.
     * Authentication is based purely on presence of JWT.
     */
    isAuthenticated: (state): boolean => {
      return !!state.token
    },

    /**
     * Moderator role check.
     * Role information comes from backend (/auth/me),
     * NOT from JWT parsing.
     */
    isModerator: (state): boolean => {
      return isModeratorRole(state.user?.role)
    },

    /**
     * Alias for isModerator (backward compatibility).
     */
    isAdmin: (state): boolean => {
      return isModeratorRole(state.user?.role)
    },
  },

  // --------------------------------------------------
  // Actions
  // --------------------------------------------------
  actions: {
    /**
     * Initializes auth state on application startup.
     *
     * Purpose:
     * - Restore user profile if JWT token exists
     * - Keep auth state consistent after page reload
     *
     * Behavior:
     * - If no token -> do nothing
     * - If token exists and user already loaded -> do nothing
     * - If token exists but user missing -> load /auth/me
     *
     * Notes:
     * - Does NOT redirect
     * - Does NOT log out user aggressively
     * - Router guards decide access control
     */
    async init(): Promise<void> {
      if (!this.token || this.user) {
        return
      }

      logger.log('AUTH init started')

      try {
        const response = await http.get<User>('/auth/me')
        this.user = response.data

        logger.log('AUTH init successful', {
          userId: this.user.id,
          role: this.user.role,
        })
      } catch (error) {
        logger.warn('AUTH init failed, clearing auth state')

        // Token is invalid / expired / revoked
        this.token = null
        this.user = null
        localStorage.removeItem('token')
      }
    },

    /**
     * Performs user login.
     *
     * Flow:
     * 1. Call /auth/login with credentials
     * 2. Store JWT token
     * 3. Load current user profile via /auth/me
     *
     * Notes:
     * - CSRF is NOT required
     * - JWT is sent automatically via http interceptor
     */
    async login(email: string, password: string): Promise<void> {
      logger.log('AUTH login started', { email })

      try {
        // Authenticate credentials and receive JWT
        const response = await authApi.login({ email, password })

        // Persist JWT token
        this.token = response.token
        localStorage.setItem('token', response.token)

        logger.log('AUTH JWT token stored')

        // Load user profile after successful login
        const me = await http.get<User>('/auth/me')
        this.user = me.data

        logger.log('AUTH user profile loaded', {
          userId: this.user.id,
          role: this.user.role,
        })
      } catch (error) {
        logger.error('AUTH login failed', error)

        // Cleanup in case of partial failure
        this.token = null
        this.user = null
        localStorage.removeItem('token')

        throw error
      }
    },

    /**
     * Logs out current user.
     *
     * Effects:
     * - Removes JWT token
     * - Clears user profile
     * - Does NOT call backend (stateless logout)
     */
    async logout(): Promise<void> {
      logger.log('AUTH logout')

      this.token = null
      this.user = null
      localStorage.removeItem('token')
      
      // Clear favourites on logout
      try {
        const { useFavouritesStore } = await import('@/modules/favourites/store/favourites.store')
        const favouritesStore = useFavouritesStore()
        favouritesStore.clearFavourites()
      } catch (e) {
        // Favourites store might not be loaded
      }
    },
  },
})
