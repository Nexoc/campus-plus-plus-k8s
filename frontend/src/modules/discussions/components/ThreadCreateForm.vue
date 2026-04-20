<template>
  <div class="thread-create-form">
    <h3>Start a New Discussion</h3>
    
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <label for="title">Thread Title</label>
        <input
          id="title"
          v-model="formData.title"
          type="text"
          placeholder="What would you like to discuss?"
          required
          maxlength="255"
          @keydown.enter.prevent
        />
      </div>

      <div class="form-group">
        <label for="content">Description (optional)</label>
        <textarea
          id="content"
          v-model="formData.content"
          placeholder="Add more details about your discussion..."
          rows="4"
          @keydown.enter.exact.prevent="formData.content += '\n'"
        ></textarea>
      </div>

      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <div class="form-actions">
        <button type="submit" :disabled="isLoading" class="btn-primary">
          {{ isLoading ? 'Creating...' : 'Create Thread' }}
        </button>
        <button type="button" @click="resetForm" class="btn-secondary">
          Cancel
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDiscussionsStore } from '../store/discussions.store'
import { useAuthStore } from '@/modules/auth/store/auth.store'

interface Props {
  courseId: string
}

interface Emits {
  (e: 'thread-created'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const discussionsStore = useDiscussionsStore()
const authStore = useAuthStore()

const formData = ref({
  title: '',
  content: '',
})

const error = ref<string | null>(null)
const isLoading = computed(() => discussionsStore.loading)

async function handleSubmit() {
  error.value = null

  if (!authStore.isAuthenticated) {
    error.value = 'You must be logged in to create a thread'
    return
  }

  try {
    await discussionsStore.createThread(props.courseId, {
      title: formData.value.title,
      content: formData.value.content || undefined,
    })
    resetForm()
    emit('thread-created')
  } catch (err: any) {
    error.value = err || 'Failed to create thread'
  }
}

function resetForm() {
  formData.value.title = ''
  formData.value.content = ''
  error.value = null
}
</script>

<style scoped>
.thread-create-form {
  padding: 1.5rem;
  background: var(--color-surface);
  border-radius: 8px;
  margin-bottom: 2rem;
  border: 1px solid var(--color-border);
}

.thread-create-form h3 {
  margin-top: 0;
  color: var(--color-text-primary);
  font-size: 1.1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: var(--color-text-primary);
  font-weight: 500;
  font-size: 0.95rem;
}

.form-group input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-size: 1rem;
  font-family: inherit;
  background: var(--color-background);
  color: var(--color-text-primary);
}

.form-group input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
}

.error-message {
  padding: 0.75rem;
  margin-bottom: 1rem;
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
  border-radius: 4px;
  font-size: 0.9rem;
}

:root[data-theme='dark'] .error-message {
  background: #3d2021;
  color: #f8d7da;
  border-color: #5c2527;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
}

.btn-primary,
.btn-secondary {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.3s ease;
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
</style>
