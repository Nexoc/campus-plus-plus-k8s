<script setup lang="ts">
import { useAuthStore } from '@/modules/auth/store/auth.store'
import CourseMaterialsSection from '@/modules/courses/components/CourseMaterialsSection.vue'
import FavouriteButton from '@/modules/favourites/components/FavouriteButton.vue'
import WatchButton from '@/shared/components/WatchButton.vue'
import { useFavouritesStore } from '@/modules/favourites/store/favourites.store'
import ReviewsSection from '@/modules/reviews/components/ReviewsSection.vue'
import { sanitizeHtml, sanitizeUrl } from '@/shared/utils/sanitizeHtml'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { coursesApi } from '../api/coursesApi'
import type { Course, RichBlock } from '../model/Course'
import DiscussionsSection from '@/modules/discussions/components/DiscussionsSection.vue'

const route = useRoute()
const authStore = useAuthStore()
const favouritesStore = useFavouritesStore()

const course = ref<Course | null>(null)
const loading = ref(false)
const error = ref('')
const sanitizedDetailsHtml = computed(() => sanitizeHtml(course.value?.detailsHtml))
const safeSourceUrl = computed(() => sanitizeUrl(course.value?.sourceUrl))

function normalizeBlocks(raw: unknown): RichBlock[] {
  if (typeof raw === 'string') {
    return [{ type: 'text', content: sanitizeHtml(raw) }]
  }

  if (!Array.isArray(raw)) return []

  return raw
    .map((item) => {
      if (typeof item === 'string') {
        return { type: 'text', content: sanitizeHtml(item) } as RichBlock
      }
      if (item && typeof item === 'object') {
        const block = item as Record<string, unknown>
        if (block.type === 'text' && typeof block.content === 'string') {
          return { type: 'text', content: sanitizeHtml(block.content) } as RichBlock
        }
        if (block.type === 'list' && Array.isArray(block.items)) {
          const items = block.items
            .filter((entry) => typeof entry === 'string')
            .map((entry) => sanitizeHtml(entry))
          return { type: 'list', items, ordered: Boolean(block.ordered) } as RichBlock
        }
      }
      return null
    })
    .filter((b): b is RichBlock => Boolean(b))
}

const sections = computed(() => {
  if (!course.value) return []
  if (sanitizedDetailsHtml.value) return []
  return [
    { title: 'Content', blocks: normalizeBlocks(course.value.content) },
    { title: 'Learning Outcomes', blocks: normalizeBlocks(course.value.learningOutcomes) },
    { title: 'Teaching Method', blocks: normalizeBlocks(course.value.teachingMethod) },
    { title: 'Exam Method', blocks: normalizeBlocks(course.value.examMethod) },
    { title: 'Literature', blocks: normalizeBlocks(course.value.literature) },
    { title: 'Teaching Language', blocks: normalizeBlocks(course.value.teachingLanguage) },
  ].filter((section) => section.blocks.length > 0)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id as string
    course.value = (await coursesApi.getById(id)).data

    if (authStore.isAuthenticated && !favouritesStore.loaded) {
      await favouritesStore.loadFavourites()
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load course'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <div class="page-card">
      <template v-if="course">
        <h1>{{ course.title }}</h1>

        <div v-if="authStore.isAuthenticated" class="action-buttons">
          <FavouriteButton
            v-if="course.courseId"
            :course-id="course.courseId"
            :show-label="true"
            :size="20"
          />
          <WatchButton
            v-if="course.courseId"
            target-type="COURSE"
            :target-id="course.courseId"
          />
        </div>

        <p class="entity-meta">
          <span v-if="course.studyProgram" class="study-program">
            <router-link :to="{ name: 'StudyProgramDetail', params: { id: course.studyProgram.id } }">
              📚 {{ course.studyProgram.name }}
            </router-link>
          </span>
          <span v-if="course.studyProgram && course.ects"> · </span>
          <span v-if="course.ects">ECTS: {{ course.ects }}</span>
          <span v-if="course.language"> · Language: {{ course.language }}</span>
          <span v-if="course.sws"> · SWS: {{ course.sws }}</span>
          <span v-if="course.semester"> · Semester: {{ course.semester }}</span>
          <span v-if="course.kind"> · Type: {{ course.kind }}</span>
        </p>

        <div v-if="course.description" class="entity-description">
          {{ course.description }}
        </div>

        <div class="course-section" v-if="sanitizedDetailsHtml">
          <h3>Details</h3>
          <div class="html-content" v-html="sanitizedDetailsHtml"></div>
        </div>

        <div v-else>
          <div
            class="course-section"
            v-for="section in sections"
            :key="section.title"
          >
            <h3>{{ section.title }}</h3>
            <div
              class="rich-block"
              v-for="(block, idx) in section.blocks"
              :key="`${section.title}-${idx}`"
            >
              <p v-if="block.type === 'text'" v-html="block.content"></p>
              <ul v-else-if="block.type === 'list' && !block.ordered">
                <li v-for="(item, i) in block.items" :key="i" v-html="item"></li>
              </ul>
              <ol v-else-if="block.type === 'list' && block.ordered">
                <li v-for="(item, i) in block.items" :key="i" v-html="item"></li>
              </ol>
            </div>
          </div>
        </div>

        <div class="course-section" v-if="safeSourceUrl">
          <h3>Source</h3>
          <a :href="safeSourceUrl" target="_blank" rel="noreferrer">
            {{ safeSourceUrl }}
          </a>
        </div>

        <CourseMaterialsSection
          v-if="authStore.isAuthenticated && course.courseId"
          :courseId="course.courseId"
        />

        <ReviewsSection :courseId="course.courseId!" />

        <!-- Discussions Section -->
        <DiscussionsSection :courseId="course.courseId!" />
      </template>

      <template v-else>
        <div v-if="loading">Loading...</div>
        <div v-if="error" class="error-message">{{ error }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  width: 100%;
  background: var(--color-bg);
  padding: var(--space-lg) 0;
  min-height: calc(100vh - var(--navbar-height));
}

.page-card {
  max-width: 900px;
  margin: 0 auto;
  padding: var(--space-2xl);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  margin-left: var(--container-padding);
  margin-right: var(--container-padding);
}

.detail-page h1 {
  text-align: center;
  margin: 0 0 var(--space-lg);
  color: var(--color-text);
  font-size: var(--font-2xl);
  font-weight: 700;
}

.entity-meta {
  text-align: center;
  color: var(--color-text);
  font-size: var(--font-sm);
  margin-bottom: var(--space-lg);
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  line-height: 1.6;
}

.study-program {
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  display: inline-block;
  transition: all 0.2s ease;
}

.study-program a {
  color: var(--color-primary);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  transition: all 0.2s ease;
}

.study-program a:hover {
  color: var(--color-primary-hover);
  text-decoration: underline;
}

.study-program:hover {
  background: var(--color-primary);
  color: white;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.2);
}

.study-program:hover a {
  color: white;
}

.entity-description {
  text-align: center;
  color: var(--color-text);
  margin-bottom: var(--space-2xl);
  padding: var(--space-lg);
  background: var(--color-primary-light);
  border-left: 4px solid var(--color-primary);
  border-radius: var(--radius-sm);
  font-style: italic;
}

.action-buttons {
  display: flex;
  gap: var(--space-lg);
  justify-content: center;
  margin-bottom: var(--space-2xl);
  flex-wrap: wrap;
}

.course-section {
  margin-top: 2rem;
  padding-top: 2rem;
  border-top: 1px solid var(--color-border);
}

.course-section h3 {
  color: var(--color-text);
  font-weight: 600;
  margin-bottom: 1rem;
}

.html-content {
  color: var(--color-text);
  line-height: 1.8;
}

.rich-block {
  margin-bottom: 1rem;
  color: var(--color-text);
  line-height: 1.8;
}

.rich-block p,
.rich-block ul,
.rich-block ol {
  margin: 0.5rem 0;
}

.rich-block li {
  margin-left: 1.5rem;
}

.error-message {
  padding: 1rem;
  background: #fee2e2;
  color: #dc2626;
  border: 1px solid #fca5a5;
  border-radius: var(--radius-sm);
  margin-bottom: 1rem;
}

.favourite-button-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

/* Responsive adjustments */
@media (max-width: 639px) {
  .page-card {
    padding: var(--space-lg);
    margin-left: 0;
    margin-right: 0;
  }

  .detail-page h1 {
    font-size: var(--font-xl);
  }

  .entity-meta {
    font-size: var(--font-xs);
  }

  .action-buttons {
    flex-direction: column;
    align-items: center;
  }

  .action-buttons button,
  .action-buttons a {
    width: 100%;
    min-height: 44px;
  }
}
</style>
