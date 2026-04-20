<template>
  <div class="post-create-form">
    <h4>Add a post</h4>
    
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <textarea
          v-model="formData.content"
          placeholder="Share your thoughts, ask a question..."
          rows="4"
          required
          @keydown.enter.exact.prevent="formData.content += '\n'"
        ></textarea>
      </div>

      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <div class="form-actions">
        <button type="submit" :disabled="isLoading" class="btn-primary">
          {{ isLoading ? 'Posting...' : 'Post' }}
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
  threadId: string
}

interface Emits {
  (e: 'post-created'): void
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
    await discussionsStore.createPost(props.threadId, {
      content: formData.value.content,
    })
    resetForm()
    emit('post-created')
  } catch (err: any) {
    error.value = err || 'Failed to create post'
  }
}

function resetForm() {
  formData.value.content = ''
  error.value = null
}
</script>

<style scoped>
.post-create-form {
  padding: 1rem;
  background: var(--color-surface);
  border-radius: 8px;
  margin-bottom: 1.5rem;
  border: 1px solid var(--color-border);
}

.post-create-form h4 {
  margin-top: 0;
  color: var(--color-text-primary);
  font-size: 1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-size: 1rem;
  font-family: inherit;
  resize: vertical;
  background: var(--color-background);
  color: var(--color-text-primary);
}

.form-group textarea:focus {
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
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 0.9rem;
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
  background: var(--color-surface);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.btn-secondary:hover {
  background: var(--color-background);
}
</style>
