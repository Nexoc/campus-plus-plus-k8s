import type { CampusBuilding } from './CampusBuilding'

export interface StudyProgramCourseSummary {
  courseId: string
  title: string
  ects?: number | null
  language?: string | null
}

export interface StudyProgramModule {
  moduleId: string
  title: string
  semester?: number | null
  courses: StudyProgramCourseSummary[]
}

export interface StudyProgramDetail {
  studyProgramId: string
  name: string
  description?: string | null
  degree?: string | null
  semesters?: number | null
  mode?: string | null
  totalEcts?: number | null
  language?: string | null
  buildingName?: string | null
  buildingLat?: number | null
  buildingLon?: number | null
  modules: StudyProgramModule[]
  campusBuildings?: CampusBuilding[]
}
