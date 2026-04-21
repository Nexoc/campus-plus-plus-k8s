<template>
  <div class="thread-detail">
    <div v-if="loading" class="loading">Loading thread...</div>

    <template v-else-if="thread">
      <div class="thread-header">
        <div class="thread-title-row">
          <h2 v-if="!isEditingThread">{{ thread.title }}</h2>
          <div class="thread-actions" v-if="!isEditingThread">
            <WatchButton
              v-if="authStore.isAuthenticated"
              target-type="THREAD"
              :target-id="thread.id"
            />
            <button
              v-if="isThreadAuthorOrModerator"
              class="btn-edit"
              @click="startEditThread"
            >
              Edit
            </button>
            <button
              v-if="isThreadAuthorOrModerator"
              class="btn-delete"
              @click="confirmDeleteThread"
            >
              Delete
            </button>
          </div>
        </div>

        <div v-if="isEditingThread" class="thread-edit">
          <input
            v-model="editThreadData.title"
            type="text"
            placeholder="Thread title"
            class="edit-title"
            required
          />
          <textarea
            v-model="editThreadData.content"
            placeholder="Optional description..."
            rows="3"
            class="edit-content"
          ></textarea>
          <div class="edit-actions">
            <button @click="saveThreadEdit" class="btn-save">Save</button>
            <button @click="cancelEditThread" class="btn-cancel">Cancel</button>
          </div>
        </div>

        <p class="thread-meta" v-if="!isEditingThread">
          <span class="thread-author">{{ thread.createdByName || (thread.createdBy ? thread.createdBy.substring(0, 8) : 'Unknown') }}</span>
          <span class="separator">•</span>
          <span class="thread-date">{{ formatDate(thread.createdAt) }}</span>
        </p>
        <div v-if="thread.content && !isEditingThread" class="thread-content">
          {{ thread.content }}
        </div>
      </div>

      <!-- Create Post Form (only for authenticated users) -->
      <PostCreateForm
        v-if="authStore.isAuthenticated"
        :thread-id="thread.id"
        @post-created="onPostCreated"
      />

      <!-- Posts List -->
      <div class="posts-section">
        <h3>Discussion</h3>
        
        <div v-if="posts.length === 0" class="empty-state">
          <p>No posts yet. Be the first to share your thoughts!</p>
        </div>

        <div v-else class="posts-list">
          <PostItem
            v-for="post in posts"
            :key="post.id"
            :post="post"
            :expanded="expandedPostId === post.id"
            @toggle-expand="togglePostExpand"
            @post-updated="onPostUpdated"
            @post-deleted="onPostDeleted"
          />
        </div>
      </div>
    </template>

    <template v-else>
      <div class="error-message">Thread not found</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDiscussionsStore } from '../store/discussions.store'
import { useAuthStore } from '@/modules/auth/store/auth.store'
import { formatDate } from '@/shared/utils/dateFormatter'
import PostCreateForm from './PostCreateForm.vue'
import PostItem from './PostItem.vue'
import WatchButton from '@/shared/components/WatchButton.vue'

interface Props {
  threadId: string
}

const props = defineProps<Props>()
const router = useRouter()

const discussionsStore = useDiscussionsStore()
const authStore = useAuthStore()

const expandedPostId = ref<string | null>(null)
const isEditingThread = ref(false)
const editThreadData = ref({ title: '', content: '' })

const thread = computed(() => discussionsStore.threadDetail)
const posts = computed(() => discussionsStore.posts)
const loading = computed(() => discussionsStore.loading)

const isThreadAuthorOrModerator = computed(() => {
  return (
    authStore.isAuthenticated &&
    thread.value &&
    (authStore.user?.id === thread.value.createdBy || authStore.isModerator)
  )
})

function togglePostExpand(postId: string) {
  expandedPostId.value = expandedPostId.value === postId ? null : postId
}

function startEditThread() {
  if (thread.value) {
    editThreadData.value = {
      title: thread.value.title,
      content: thread.value.content || ''
    }
    isEditingThread.value = true
  }
}

function cancelEditThread() {
  isEditingThread.value = false
  editThreadData.value = { title: '', content: '' }
}

async function saveThreadEdit() {
  if (!thread.value || !editThreadData.value.title.trim()) return

  try {
    await discussionsStore.updateThread(thread.value.id, {
      title: editThreadData.value.title,
      content: editThreadData.value.content || undefined
    })
    isEditingThread.value = false
  } catch (error) {
    console.error('Failed to update thread:', error)
  }
}

async function confirmDeleteThread() {
  if (!thread.value) return

  if (confirm('Are you sure you want to delete this thread? This will also delete all comments. This cannot be undone.')) {
    try {
      await discussionsStore.deleteThread(thread.value.id)
      // Navigate back to course page
      if (thread.value.courseId) {
        router.push(`/courses/${thread.value.courseId}`)
      }
    } catch (error) {
      console.error('Failed to delete thread:', error)
    }
  }
}

async function onPostCreated() {
  await discussionsStore.loadPosts(props.threadId)
}

function onPostUpdated() {
  // Store handles update
}

function onPostDeleted() {
  // Store handles delete
}

// Watch for threadId changes and load data
watch(() => props.threadId, async (newThreadId) => {
  console.log('DEBUG ThreadDetail: threadId changed to:', newThreadId)
  if (newThreadId) {
    console.log('DEBUG ThreadDetail: Loading thread and posts for:', newThreadId)
    await discussionsStore.loadThreadDetail(newThreadId)
    await discussionsStore.loadPosts(newThreadId)
  }
}, { immediate: true })
</script>

<style scoped>
.thread-detail {
  padding: 1rem;
}

.loading {
  text-align: center;
  color: var(--color-text-secondary);
  padding: 2rem;
}

.thread-header {
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid var(--color-border);
}

.thread-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.thread-header h2 {
  margin: 0;
  color: var(--color-text-primary);
  flex: 1;
}

.thread-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

.btn-edit,
.btn-delete {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s ease;
}

.btn-edit {
  background: #007bff;
  color: white;
}

.btn-edit:hover {
  background: #0056b3;
}

.btn-delete {
  background: #dc3545;
  color: white;
}

.btn-delete:hover {
  background: #c82333;
}

.thread-edit {
  margin-bottom: 1rem;
}

.edit-title,
.edit-content {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-family: inherit;
  font-size: 1rem;
  margin-bottom: 0.5rem;
  background: var(--color-background);
  color: var(--color-text-primary);
}

.edit-title {
  font-weight: 600;
  font-size: 1.2rem;
}

.edit-content {
  resize: vertical;
  font-size: 0.95rem;
  white-space: pre-wrap;
}

.edit-title:focus,
.edit-content:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
}

.edit-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.btn-save {
  padding: 0.5rem 1rem;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 500;
}

.btn-save:hover {
  background: #0056b3;
}

.btn-cancel {
  padding: 0.5rem 1rem;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 500;
}

.btn-cancel:hover {
  background: #5a6268;
}

.thread-meta {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.95rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.thread-author {
  font-weight: 500;
  color: #007bff;
}

.separator {
  color: var(--color-text-secondary);
  opacity: 0.5;
}

.thread-content {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--color-surface);
  border-radius: 4px;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  line-height: 1.6;
  border: 1px solid var(--color-border);
}

.posts-section {
  margin-top: 2rem;
}

.posts-section h3 {
  color: var(--color-text-primary);
  margin-bottom: 1rem;
  font-size: 1.1rem;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  background: var(--color-surface);
  border-radius: 8px;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.error-message {
  padding: 1rem;
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
  border-radius: 4px;
  text-align: center;
}

:root[data-theme='dark'] .error-message {
  background: #3d2021;
  color: #f8d7da;
  border-color: #5c2527;
}
</style>
