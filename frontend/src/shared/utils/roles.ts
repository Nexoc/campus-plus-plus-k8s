export type NormalizedUserRole = 'APPLICANT' | 'STUDENT' | 'MODERATOR'

export function normalizeUserRole(role?: string | null): NormalizedUserRole | null {
  if (!role) {
    return null
  }

  const normalized = role.trim().toUpperCase()
  if (normalized === 'APPLICANT' || normalized === 'STUDENT' || normalized === 'MODERATOR') {
    return normalized
  }

  return null
}

export function isModeratorRole(role?: string | null): boolean {
  return normalizeUserRole(role) === 'MODERATOR'
}

export function isApplicantRole(role?: string | null): boolean {
  return normalizeUserRole(role) === 'APPLICANT'
}
