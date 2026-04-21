<template>
  <div class="post-item" :class="{ expanded }">
    <div class="post-header">
      <div class="post-meta">
        <span class="user-id">{{ post.userName || post.userId.substring(0, 8) }}</span>
        <span class="created-at">{{ formatDate(post.createdAt) }}</span>
        <span v-if="post.updatedAt !== post.createdAt" class="updated">
          (edited)
        </span>
      </div>
      <div class="post-actions">
        <button
          class="btn-expand"
          @click="toggleExpand"
          :title="expanded ? 'Hide comments' : 'Show comments'"
        >
          {{ expanded ? '−' : '+' }} {{ post.commentCount || 0 }} comments
        </button>
        <button
          v-if="isAuthorOrModerator"
          class="btn-edit"
          @click="startEdit"
          :disabled="isEditing"
        >
          Edit
        </button>
        <button
          v-if="isAuthorOrModerator"
          class="btn-delete"
          @click="confirmDelete"
        >
          Delete
        </button>
      </div>
    </div>

    <!-- Display mode -->
    <div v-if="!isEditing">
      <div class="post-content">
        {{ post.content }}
      </div>

      <!-- Reactions -->
      <div class="post-reactions">
        <ReactionButton target-type="post" :target-id="post.id" />
      </div>
    </div>

    <!-- Edit mode -->
    <div v-else class="post-edit">
      <textarea v-model="editContent" rows="4"></textarea>
      <div class="edit-actions">
        <button @click="saveEdit" class="btn-save">Save</button>
        <button @click="cancelEdit" class="btn-cancel">Cancel</button>
      </div>
    </div>

    <!-- Comments section -->
    <div v-if="expanded" class="comments-section">
      <CommentList :post-id="post.id" />

      <CommentCreateForm
        v-if="authStore.isAuthenticated"
        :post-id="post.id"
        @comment-created="onCommentCreated"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDiscussionsStore } from '../store/discussions.store'
import { useAuthStore } from '@/modules/auth/store/auth.store'
import { formatDate } from '@/shared/utils/dateFormatter'
import type { Post } from '../model/Discussion'
import CommentList from './CommentList.vue'
import CommentCreateForm from './CommentCreateForm.vue'
import ReactionButton from '@/shared/components/ReactionButton.vue'

interface Props {
  post: Post
  expanded?: boolean
}

interface Emits {
  (e: 'toggle-expand', postId: string): void
  (e: 'post-updated'): void
  (e: 'post-deleted'): void
}

const props = withDefaults(defineProps<Props>(), {
  expanded: false,
})
const emit = defineEmits<Emits>()

const discussionsStore = useDiscussionsStore()
const authStore = useAuthStore()

const isEditing = ref(false)
const editContent = ref('')

const isAuthorOrModerator = computed(() => {
  return (
    authStore.isAuthenticated &&
    (authStore.user?.id === props.post.userId || authStore.isModerator)
  )
})

function toggleExpand() {
  emit('toggle-expand', props.post.id)
}

function startEdit() {
  isEditing.value = true
  editContent.value = props.post.content
}

function cancelEdit() {
  isEditing.value = false
  editContent.value = ''
}

async function saveEdit() {
  try {
    await discussionsStore.updatePost(props.post.id, { content: editContent.value })
    isEditing.value = false
    emit('post-updated')
  } catch (error) {
    console.error('Failed to update post:', error)
  }
}

async function confirmDelete() {
  if (confirm('Are you sure you want to delete this post? This cannot be undone.')) {
    try {
      await discussionsStore.deletePost(props.post.id)
      emit('post-deleted')
    } catch (error) {
      console.error('Failed to delete post:', error)
    }
  }
}

function onCommentCreated() {
  // Comments are loaded in CommentList
}
</script>

<style scoped>
.post-item {
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  transition: all 0.3s ease;
}

.post-item.expanded {
  border-color: #007bff;
  background: var(--color-background);
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  gap: 1rem;
  flex-wrap: wrap;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.user-id {
  font-weight: 500;
  color: var(--color-text-primary);
}

.updated {
  color: var(--color-text-secondary);
  font-style: italic;
  opacity: 0.7;
}

.post-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

button {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-expand {
  background: var(--color-background);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.btn-expand:hover {
  background: var(--color-border);
}

.btn-edit {
  background: #28a745;
  color: white;
}

.btn-edit:hover:not(:disabled) {
  background: #218838;
}

.btn-edit:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-delete {
  background: #dc3545;
  color: white;
}

.btn-delete:hover {
  background: #c82333;
}

.post-content {
  margin: 1rem 0;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-primary);
}

.post-reactions {
  margin: 0.75rem 0;
}

.post-edit {
  margin: 1rem 0;
}

.post-edit textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-family: inherit;
  font-size: 1rem;
  background: var(--color-background);
  color: var(--color-text-primary);
}

.post-edit textarea:focus {
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
  background: #007bff;
  color: white;
}

.btn-save:hover {
  background: #0056b3;
}

.btn-cancel {
  background: #6c757d;
  color: white;
}

.btn-cancel:hover {
  background: #5a6268;
}

.comments-section {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--color-border);
}
</style>
