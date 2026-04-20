<template>
  <div class="comment-create-form">
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <textarea
          v-model="formData.content"
          placeholder="Write a comment..."
          rows="2"
          required
          @keydown.enter.exact.prevent="formData.content += '\n'"
        ></textarea>
      </div>

      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <div class="form-actions">
        <button type="submit" :disabled="isLoading" class="btn-primary">
          {{ isLoading ? 'Commenting...' : 'Comment' }}
        </button>
        <button type="button" @click="resetForm" class="btn-secondary">
          Clear
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDiscussionsStore } from '../store/discussions.store'

interface Props {
  postId: string
}

interface Emits {
  (e: 'comment-created'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const discussionsStore = useDiscussionsStore()

const formData = ref({
  content: '',
})

const error = ref<string | null>(null)
const isLoading = computed(() => discussionsStore.loading)

async function handleSubmit() {
  error.value = null

  try {
    await discussionsStore.createComment(props.postId, {
      content: formData.value.content,
    })
    resetForm()
    emit('comment-created')
  } catch (err: any) {
    error.value = err || 'Failed to create comment'
  }
}

function resetForm() {
  formData.value.content = ''
  error.value = null
}
</script>

<style scoped>
.comment-create-form {
  margin-top: 1rem;
}

.form-group {
  margin-bottom: 0.75rem;
}

.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.95rem;
  resize: vertical;
  background: var(--color-background);
  color: var(--color-text-primary);
}

.form-group textarea:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
}

.error-message {
  padding: 0.5rem;
  margin-bottom: 0.5rem;
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
  border-radius: 3px;
  font-size: 0.85rem;
}

:root[data-theme='dark'] .error-message {
  background: #3d2021;
  color: #f8d7da;
  border-color: #5c2527;
}

.form-actions {
  display: flex;
  gap: 0.5rem;
}

button {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s ease;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-secondary {
  background: white;
  color: #333;
  border: 1px solid #ddd;
}

.btn-secondary:hover {
  background: #f5f5f5;
}

:root[data-theme='dark'] .btn-secondary {
  background: var(--color-surface);
  color: var(--color-text-primary);
  border-color: var(--color-border);
}

:root[data-theme='dark'] .btn-secondary:hover {
  background: var(--color-background);
}
</style>
