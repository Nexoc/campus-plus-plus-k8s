// src/modules/discussions/api/discussionsApi.ts

import http from '@/app/api/http'
import type { Thread, Post, Comment } from '../model/Discussion'

export interface CreateThreadRequest {
  title: string
  content?: string
}

export interface UpdateThreadRequest {
  title: string
  content?: string
}

export interface CreatePostRequest {
  content: string
}

export interface UpdatePostRequest {
  content: string
}

export interface CreateCommentRequest {
  content: string
}

export interface UpdateCommentRequest {
  content: string
}

export const discussionsApi = {
  // Thread endpoints (public read)
  getThreadsByCourse(courseId: string) {
    return http.get<Thread[]>(`/api/public/courses/${courseId}/threads`)
  },

  getThreadById(threadId: string) {
    return http.get<Thread>(`/api/public/threads/${threadId}`)
  },

  // Post endpoints (public read)
  getPostsByThread(threadId: string) {
    return http.get<Post[]>(`/api/public/threads/${threadId}/posts`)
  },

  getPostById(postId: string) {
    return http.get<Post>(`/api/public/posts/${postId}`)
  },

  // Comment endpoints (public read)
  getCommentsByPost(postId: string) {
    return http.get<Comment[]>(`/api/public/posts/${postId}/comments`)
  },

  getCommentById(commentId: string) {
    return http.get<Comment>(`/api/public/comments/${commentId}`)
  },

  // Thread endpoints (protected write)
  createThread(courseId: string, data: CreateThreadRequest) {
    return http.post<Thread>(`/api/courses/${courseId}/threads`, data)
  },

  updateThread(threadId: string, data: UpdateThreadRequest) {
    return http.put<Thread>(`/api/threads/${threadId}`, data)
  },

  deleteThread(threadId: string) {
    return http.delete(`/api/threads/${threadId}`)
  },

  // Post endpoints (protected write)
  createPost(threadId: string, data: CreatePostRequest) {
    return http.post<Post>(`/api/threads/${threadId}/posts`, data)
  },

  updatePost(postId: string, data: UpdatePostRequest) {
    return http.put<Post>(`/api/posts/${postId}`, data)
  },

  deletePost(postId: string) {
    return http.delete(`/api/posts/${postId}`)
  },

  // Comment endpoints (protected write)
  createComment(postId: string, data: CreateCommentRequest) {
    return http.post<Comment>(`/api/posts/${postId}/comments`, data)
  },

  updateComment(commentId: string, data: UpdateCommentRequest) {
    return http.put<Comment>(`/api/comments/${commentId}`, data)
  },

  deleteComment(commentId: string) {
    return http.delete(`/api/comments/${commentId}`)
  },
}
