<template>
  <div class="comment-item">
    <div class="comment-header">
      <div class="comment-meta">
      <span class="user-id">{{ comment.userName || comment.userId.substring(0, 8) }}</span>
        <span class="created-at">{{ formatDate(comment.createdAt) }}</span>
        <span v-if="comment.updatedAt !== comment.createdAt" class="updated">
          (edited)
        </span>
      </div>
      <div class="comment-actions">
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
    <div v-if="!isEditing" class="comment-content">
      {{ comment.content }}
    </div>

    <!-- Edit mode -->
    <div v-else class="comment-edit">
      <textarea v-model="editContent" rows="2"></textarea>
      <div class="edit-actions">
        <button @click="saveEdit" class="btn-save">Save</button>
        <button @click="cancelEdit" class="btn-cancel">Cancel</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDiscussionsStore } from '../store/discussions.store'
import { useAuthStore } from '@/modules/auth/store/auth.store'
import { formatDate } from '@/shared/utils/dateFormatter'
import type { Comment } from '../model/Discussion'

interface Props {
  comment: Comment
}

interface Emits {
  (e: 'comment-updated'): void
  (e: 'comment-deleted'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const discussionsStore = useDiscussionsStore()
const authStore = useAuthStore()

const isEditing = ref(false)
const editContent = ref('')

const isAuthorOrModerator = computed(() => {
  return (
    authStore.isAuthenticated &&
    (authStore.user?.id === props.comment.userId || authStore.isModerator)
  )
})

function startEdit() {
  isEditing.value = true
  editContent.value = props.comment.content
}

function cancelEdit() {
  isEditing.value = false
  editContent.value = ''
}

async function saveEdit() {
  try {
    await discussionsStore.updateComment(props.comment.id, { content: editContent.value })
    isEditing.value = false
    emit('comment-updated')
  } catch (error) {
    console.error('Failed to update comment:', error)
  }
}

async function confirmDelete() {
  if (confirm('Are you sure you want to delete this comment? This cannot be undone.')) {
    try {
      await discussionsStore.deleteComment(props.comment.id)
      emit('comment-deleted')
    } catch (error) {
      console.error('Failed to delete comment:', error)
    }
  }
}
</script>

<style scoped>
.comment-item {
  padding: 0.75rem;
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 6px;
  margin-left: 1rem;
}

:root[data-theme='dark'] .comment-item {
  background: var(--color-surface);
  border-color: var(--color-border);
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.5rem;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.user-id {
  font-weight: 500;
  color: #007bff;
}

.updated {
  color: var(--color-text-secondary);
  font-style: italic;
  opacity: 0.7;
}

.comment-actions {
  display: flex;
  gap: 0.4rem;
}

button {
  padding: 0.3rem 0.6rem;
  border: none;
  border-radius: 3px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s ease;
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

.comment-content {
  font-size: 0.95rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-primary);
}

.comment-edit {
  margin: 0.5rem 0;
}

.comment-edit textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 3px;
  font-family: inherit;
  font-size: 0.95rem;
  resize: vertical;
  background: white;
  color: #000;
}

:root[data-theme='dark'] .comment-edit textarea {
  background: var(--color-background);
  color: var(--color-text-primary);
  border-color: var(--color-border);
}

.comment-edit textarea:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
}

.edit-actions {
  display: flex;
  gap: 0.4rem;
  margin-top: 0.4rem;
}

.btn-save {
  background: #007bff;
  color: white;
  padding: 0.3rem 0.6rem;
}

.btn-save:hover {
  background: #0056b3;
}

.btn-cancel {
  background: #6c757d;
  color: white;
  padding: 0.3rem 0.6rem;
}

.btn-cancel:hover {
  background: #5a6268;
}
</style>
