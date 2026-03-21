<!--
  src/modules/home/pages/HomePage.vue

  Home page for authenticated users.

  Responsibilities:
  - Entry point after login
  - Navigation only
-->

<template>
  <div class="detail-page">
    <div class="page-card home-page">
      <h1>Welcome</h1>

      <p class="home-subtitle">
        You are on <strong>Campus++</strong>.
      </p>

      <div class="home-content">
        <p>
          This platform provides a secure environment for managing users,
          roles, and access within the system.
        </p>

        <p>
          Use the navigation bar above to move between sections.
          Your access level determines which features are available to you.
        </p>
      </div>

      <div class="navigation-buttons">
        <RouterLink to="/programs">
          <button class="base-button">
            Study Programs
          </button>
        </RouterLink>

        <RouterLink to="/courses">
          <button class="base-button">
            Courses
          </button>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.detail-page {
  padding: var(--container-padding);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  width: 100%;
  box-sizing: border-box;
  min-height: calc(100vh - var(--navbar-height));
}

.page-card {
  width: 100%;
  max-width: 900px;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: var(--space-2xl);
  box-shadow: var(--shadow-md);
  color: var(--color-text);
  box-sizing: border-box;
}

@media (max-width: 639px) {
  .page-card {
    padding: var(--space-lg);
    margin: var(--space-lg) auto;
  }
}

.home-page {
  text-align: center;
}

.page-card h1 {
  margin: 0 0 var(--space-lg) 0;
  font-size: var(--font-2xl);
  color: var(--color-text);
  font-weight: 700;
}

@media (max-width: 639px) {
  .page-card h1 {
    font-size: var(--font-xl);
  }
}

.home-subtitle {
  font-size: 1.125rem;
  color: var(--color-primary);
  margin-bottom: var(--space-xl);
  font-weight: 600;
}

.home-content {
  text-align: left;
  margin-bottom: var(--space-2xl);
  line-height: 1.7;
  background: var(--color-primary-light);
  padding: var(--space-lg);
  border-radius: var(--radius-md);
  border-left: 4px solid var(--color-primary);
  color: var(--color-text);
}

.home-content p {
  margin: var(--space-md) 0;
  font-size: var(--font-base);
}

.navigation-buttons {
  display: flex;
  gap: var(--space-lg);
  justify-content: center;
  flex-wrap: wrap;
  margin-top: var(--space-2xl);
}

.navigation-buttons a {
  text-decoration: none;
  display: inline-block;
}

.navigation-buttons .base-button {
  min-width: 200px;
  font-size: var(--font-base);
  font-weight: 700;
  padding: var(--space-md) var(--space-lg);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.navigation-buttons .base-button:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}
</style>