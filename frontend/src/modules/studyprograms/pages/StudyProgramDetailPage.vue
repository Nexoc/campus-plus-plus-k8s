<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { studyProgramsApi } from '../api/studyProgramsApi'
import type { StudyProgramDetail } from '../model/StudyProgramDetail'
import StudyProgramFavouriteButton from '@/modules/favourites/components/StudyProgramFavouriteButton.vue'
import { useFavouritesStore } from '@/modules/favourites/store/favourites.store'
import { useAuthStore } from '@/modules/auth/store/auth.store'

const CampusMap = defineAsyncComponent(() => import('../components/CampusMap.vue'))

const route = useRoute()
const authStore = useAuthStore()
const favouritesStore = useFavouritesStore()
const program = ref<StudyProgramDetail | null>(null)
const loading = ref(false)
const error = ref('')

const isAuthenticated = computed(() => authStore.isAuthenticated)

function slugify(text: string) {
  return text
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id as string
    const response = await studyProgramsApi.getDetails(id)
    program.value = response.data
    
    // Load study program favourites if authenticated
    if (isAuthenticated.value) {
      await favouritesStore.loadStudyProgramFavourites()
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load program'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <div class="page-card">
      <template v-if="program">
        <h1>{{ program.name }}</h1>
        <div v-if="isAuthenticated" class="favourite-button-wrapper">
          <StudyProgramFavouriteButton 
            v-if="program.studyProgramId"
            :study-program-id="program.studyProgramId"
            :show-label="true"
            :size="20"
          />
        </div>
        <p class="entity-meta">
          <span v-if="program.degree">Degree: {{ program.degree }}</span>
          <span v-if="program.semesters"> · Semesters: {{ program.semesters }}</span>
          <span v-if="program.totalEcts"> · ECTS: {{ program.totalEcts }}</span>
          <span v-if="program.mode"> · Mode: {{ program.mode }}</span>
          <span v-if="program.language"> · Language: {{ program.language }}</span>
        </p>
        <div v-if="program.description" class="entity-description">
          {{ program.description }}
        </div>
        <CampusMap :buildings="program.campusBuildings ?? []" />
        <div class="modules-list">
          <div
            v-for="m in program.modules"
            :key="m.moduleId"
            class="module-card"
          >
            <div class="module-header">
              <h2>{{ m.title }}</h2>
              <span v-if="m.semester" class="module-semester">Semester {{ m.semester }}</span>
            </div>
            <table class="module-courses">
              <thead>
                <tr>
                  <th>Course</th>
                  <th>ECTS</th>
                  <th>Language</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in m.courses" :key="c.courseId">
                  <td>
                    <router-link :to="{ name: 'CourseDetail', params: { id: c.courseId, slug: slugify(c.title) } }">
                      {{ c.title }}
                    </router-link>
                  </td>
                  <td>{{ c.ects ?? '' }}</td>
                  <td>{{ c.language ?? '' }}</td>
                </tr>
                <tr v-if="m.courses.length === 0">
                  <td colspan="3" class="empty-row">No courses in this module</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
      <template v-else>
        <div v-if="loading">Loading...</div>
        <div v-if="error" class="error-message">{{ error }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.detail-page h1 {
  text-align: center;
  margin-bottom: 20px;
}

.favourite-button-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}
</style>
