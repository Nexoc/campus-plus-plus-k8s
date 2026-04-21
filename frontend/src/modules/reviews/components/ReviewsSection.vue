<template>
  <CollapsibleSection
    title="Reviews"
    :show-toggle="auth.isAuthenticated && !isApplicantRole(auth.user?.role) && (!userHasReviewed || editingId !== null)"
    show-label="Write a Review"
    hide-label="Hide Form"
  >
    <template #summary>
      <!-- Average Rating Display -->
      <div v-if="summary" class="rating-display">
        <span class="average-rating">
          <template v-if="summary.averageRating !== null">
            ★ {{ summary.averageRating.toFixed(1) }} / 5
          </template>
          <template v-else>
            ★ No ratings yet
          </template>
        </span>
        <span class="review-count">
          ({{ summary.reviewCount }} {{ summary.reviewCount === 1 ? 'review' : 'reviews' }})
        </span>
      </div>

      <!-- Success Message -->
      <div v-if="successMessage" class="success-message">
        {{ successMessage }}
      </div>
    </template>

    <template #form>
      <!-- Create/Edit Review Form -->
      <form @submit.prevent="submitReview" class="review-form">
        <h4>{{ editingId ? 'Edit Your Review' : 'Write a Review' }}</h4>
        
        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <div class="form-group">
          <label>Rating <span class="required">*</span></label>
          <select v-model.number="formData.rating" required class="form-select">
            <option value="">Select rating...</option>
            <option value="5">5 - Excellent</option>
            <option value="4">4 - Good</option>
            <option value="3">3 - Average</option>
            <option value="2">2 - Poor</option>
            <option value="1">1 - Very Poor</option>
          </select>
        </div>

        <div class="form-group">
          <label>Review Text <span class="optional">(optional)</span></label>
          <textarea
            v-model="formData.text"
            placeholder="Share your thoughts about this course..."
            rows="5"
            class="form-textarea"
          ></textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="base-button" :disabled="loading || !formData.rating">
            {{ loading ? 'Submitting...' : (editingId ? 'Update Review' : 'Post Review') }}
          </button>
          <button 
            type="button"
            class="base-button secondary"
            @click="cancelCreate"
          >
            Cancel
          </button>
        </div>
      </form>
    </template>

    <template #content>
      <!-- Reviews List -->
      <div v-if="reviews.length > 0" class="reviews-list">
        <!-- Sort Controls -->
        <div class="list-header">
          <h4>All Reviews</h4>
          <div class="sort-controls">
            <label for="review-sort">Sort by:</label>
            <select 
              id="review-sort"
              v-model="sortOption" 
              @change="onSortChange"
              class="sort-select"
            >
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="highest_rating">Highest rating</option>
              <option value="lowest_rating">Lowest rating</option>
            </select>
          </div>
        </div>

        <article v-for="review in reviews" :key="review.reviewId" class="review-item">
          <div class="item-header">
            <div class="item-meta">
              <strong>{{ review.userName || 'Anonymous' }}</strong>
              <span class="meta-date">{{ formatDate(review.createdAt) }}</span>
              <span class="meta-rating">★ {{ review.rating }}/5</span>
            </div>

            <!-- Edit/Delete for own reviews -->
            <div v-if="isOwnReview(review)" class="item-actions">
              <button 
                class="base-button small"
                @click="startEdit(review)"
              >
                Edit
              </button>
              <button 
                class="base-button small danger"
                @click="deleteReview(review.reviewId!)"
              >
                Delete
              </button>
            </div>

            <!-- Report button for other users' reviews -->
            <div v-else-if="canReportReview(review)" class="item-actions">
              <button 
                class="base-button small"
                @click="openReportModal(review.reviewId!)"
                title="Report inappropriate review"
              >
                Report
              </button>
            </div>
          </div>

          <p v-if="review.text" class="item-content">{{ review.text }}</p>
          <p v-else class="item-content no-text">No additional comments</p>

          <!-- Reactions -->
          <div class="item-reactions">
            <ReactionButton target-type="review" :target-id="review.reviewId!" />
          </div>
        </article>
      </div>

      <!-- No reviews message -->
      <div v-else class="empty-state">
        <p>No reviews yet. Be the first to share your thoughts!</p>
      </div>
    </template>
  </CollapsibleSection>

  <!-- Report Modal -->
  <ReportModal
    :is-open="showReportModal"
    target-type="REVIEW"
    :target-id="reportingReviewId"
    @close="closeReportModal"
    @success="handleReportSuccess"
  />
</template>

<script setup lang="ts">
import { useAuthStore } from '@/modules/auth/store/auth.store'
import { isApplicantRole } from '@/shared/utils/roles'
import { computed, ref } from 'vue'
import { reviewsApi } from '../api/reviewsApi'
import type { Review, ReviewSummary, CreateReviewRequest } from '../model/Review'
import ReportModal from '@/modules/reports/components/ReportModal.vue'
import ReactionButton from '@/shared/components/ReactionButton.vue'
import CollapsibleSection from '@/shared/components/CollapsibleSection.vue'

interface Props {
  courseId: string
}

const props = defineProps<Props>()
const auth = useAuthStore()

const reviews = ref<Review[]>([])
const summary = ref<ReviewSummary | null>(null)
const loading = ref(false)
const editingId = ref<string | null>(null)
const error = ref<string>('')
const successMessage = ref<string>('')
const sortOption = ref<string>('newest')

// Report modal state
const showReportModal = ref(false)
const reportingReviewId = ref('')
const reportedReviewIds = ref<Set<string>>(new Set())

const formData = ref<CreateReviewRequest>({
  courseId: props.courseId,
  rating: 0,
  text: '',
})

// Computed
const currentUserId = computed(() => auth.user?.id || '')

const userHasReviewed = computed(() => {
  return reviews.value.some(review => review.userId === currentUserId.value)
})

// Methods
const isOwnReview = (review: Review) => {
  return auth.isAuthenticated && review.userId === currentUserId.value
}

const canReportReview = (review: Review) => {
  // Only authenticated users who are not the review author can report
  // Applicants (unauthenticated) cannot report
  // Cannot report if already reported
  return auth.isAuthenticated &&
         !isApplicantRole(auth.user?.role) &&
         !isOwnReview(review) &&
         !reportedReviewIds.value.has(review.reviewId || '')
}

const openReportModal = (reviewId: string) => {
  reportingReviewId.value = reviewId
  showReportModal.value = true
}

const closeReportModal = () => {
  showReportModal.value = false
  reportingReviewId.value = ''
}

const handleReportSuccess = () => {
  // Mark the review as reported
  if (reportingReviewId.value) {
    reportedReviewIds.value.add(reportingReviewId.value)
  }
  
  // Close the modal
  closeReportModal()
  
  // Show success message
  successMessage.value = 'Report submitted successfully. Thank you for helping maintain our community standards.'
  setTimeout(() => {
    successMessage.value = ''
  }, 5000)
}

const formatDate = (date?: string) => {
  if (!date) return 'Unknown date'
  return new Date(date).toLocaleDateString()
}

const loadReviews = async () => {
  try {
    const response = await reviewsApi.getByCourse(props.courseId, sortOption.value)
    reviews.value = response.data
  } catch (err: any) {
    console.error('Failed to load reviews', err)
    error.value = err.response?.data?.message || 'Failed to load reviews'
  }
}

const onSortChange = async () => {
  await loadReviews()
}

const loadSummary = async () => {
  try {
    const response = await reviewsApi.getSummary(props.courseId)
    summary.value = response.data
  } catch (err: any) {
    console.error('Failed to load review summary', err)
  }
}

const submitReview = async () => {
  // Validate rating
  if (!formData.value.rating || formData.value.rating < 1 || formData.value.rating > 5) {
    error.value = 'Please select a rating between 1 and 5'
    return
  }

  error.value = ''
  loading.value = true
  
  try {
    if (editingId.value) {
      await reviewsApi.update(editingId.value, {
        rating: formData.value.rating,
        text: formData.value.text,
      })
      successMessage.value = 'Review updated successfully!'
      editingId.value = null
    } else {
      await reviewsApi.create(props.courseId, formData.value)
      successMessage.value = 'Review posted successfully!'
    }
    
    resetForm()
    await loadReviews()
    await loadSummary()
    
    // Clear success message after 3 seconds
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  } catch (err: any) {
    console.error('Failed to submit review', err)
    error.value = err.response?.data?.message || 'Failed to submit review'
  } finally {
    loading.value = false
  }
}

const cancelCreate = () => {
  resetForm()
  editingId.value = null
  error.value = ''
}

const resetForm = () => {
  formData.value = {
    courseId: props.courseId,
    rating: 0,
    text: '',
  }
}

const startEdit = (review: Review) => {
  editingId.value = review.reviewId || null
  formData.value = {
    courseId: props.courseId,
    rating: review.rating,
    text: review.text || '',
  }
  error.value = ''
}

const deleteReview = async (reviewId: string) => {
  if (confirm('Are you sure you want to delete this review?')) {
    try {
      await reviewsApi.delete(reviewId)
      await loadReviews()
      await loadSummary()
      successMessage.value = 'Review deleted successfully'
      setTimeout(() => {
        successMessage.value = ''
      }, 3000)
    } catch (err: any) {
      console.error('Failed to delete review', err)
      error.value = err.response?.data?.message || 'Failed to delete review'
    }
  }
}

// Load reviews and summary on mount
loadReviews()
loadSummary()
</script>
<style scoped>
/* Rating display */
.rating-display {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.average-rating {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-warning);
}

.review-count {
  font-size: 0.9rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

/* Messages */
.success-message {
  background: #ecfdf5;
  color: #065f46;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-sm);
  margin-top: 1rem;
  border: 1px solid #a7f3d0;
  font-weight: 500;
}

html[data-theme="dark"] .success-message {
  background: #064e3b;
  color: #a7f3d0;
  border-color: #047857;
}

.error-message {
  background: #fef2f2;
  color: #7f1d1d;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-sm);
  margin-bottom: 1rem;
  border: 1px solid #fecaca;
  font-weight: 500;
}

html[data-theme="dark"] .error-message {
  background: #7f1d1d;
  color: #fecaca;
  border-color: #dc2626;
}

/* Form */
.review-form h4 {
  margin: 0 0 1rem;
  color: var(--color-text);
  font-weight: 600;
}

.form-group {
  margin-bottom: 1.25rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: var(--color-text);
}

.form-group .required {
  color: var(--color-danger);
}

.form-group .optional {
  color: var(--color-text-muted);
  font-weight: normal;
  font-size: 0.9rem;
}

.form-select,
.form-textarea {
  width: 100%;
  padding: 0.625rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-family: inherit;
  background: var(--color-input-bg);
  color: var(--color-text);
  font-size: 0.95rem;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-select {
  cursor: pointer;
}

.form-textarea {
  min-height: 100px;
  resize: vertical;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.5rem;
  flex-wrap: wrap;
}

/* List */
.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--color-border);
  flex-wrap: wrap;
  gap: 1rem;
}

.list-header h4 {
  margin: 0;
  color: var(--color-text);
  font-size: 1.1rem;
  font-weight: 600;
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.sort-controls label {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  font-weight: 500;
  white-space: nowrap;
}

.sort-select {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-input-bg);
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.875rem;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.sort-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* Review items */
.review-item {
  background: var(--color-surface);
  padding: 1.25rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.review-item:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.75rem;
  gap: 1rem;
  flex-wrap: wrap;
}

.item-meta {
  display: flex;
  gap: 0.75rem;
  font-size: 0.875rem;
  color: var(--color-text-muted);
  flex-wrap: wrap;
  align-items: center;
}

.item-meta strong {
  color: var(--color-text);
  font-weight: 600;
}

.meta-date {
  opacity: 0.8;
}

.meta-rating {
  font-weight: 600;
  color: var(--color-warning);
}

.item-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.item-content {
  color: var(--color-text);
  line-height: 1.6;
  margin: 0.75rem 0;
}

.item-content.no-text {
  font-style: italic;
  color: var(--color-text-muted);
}

.item-reactions {
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-muted);
  font-style: italic;
  background: var(--color-primary-light);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

@media (max-width: 639px) {
  .list-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .sort-controls {
    width: 100%;
    flex-direction: column;
    align-items: flex-start;
  }

  .sort-select {
    width: 100%;
  }
}
</style>
