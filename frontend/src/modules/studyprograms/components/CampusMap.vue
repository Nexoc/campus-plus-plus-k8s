<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Map as MapLibreMap } from 'maplibre-gl'
import type { CampusBuilding } from '../model/CampusBuilding'
import campusPlanImage from '@/assets/HCW_Lageplan.jpg'

const props = defineProps<{
  buildings?: CampusBuilding[]
}>()

const mapContainer = ref<HTMLDivElement>()
const map = ref<MapLibreMap>()
const showModal = ref(false)
let maplibreModulePromise: Promise<typeof import('maplibre-gl')> | null = null

const openModal = () => {
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
}

const hasBuildings = computed(() => {
  return props.buildings && props.buildings.length > 0
})

const buildingsList = computed(() => {
  return (props.buildings || []).filter(b => Number.isFinite(b.lat) && Number.isFinite(b.lon))
})

onMounted(() => {
  void renderMap()
})

watch(buildingsList, () => {
  void renderMap()
})

onBeforeUnmount(() => {
  destroyMap()
})

async function renderMap() {
  destroyMap()

  if (!mapContainer.value || !hasBuildings.value || buildingsList.value.length === 0) {
    return
  }

  const buildings = buildingsList.value
  const firstBuilding = buildings[0]
  const maplibregl = await loadMapLibre()

  map.value = new maplibregl.Map({
    container: mapContainer.value,
    style: 'https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=90ff8868d0704abe832a7f36cd54df06',
    center: [firstBuilding.lon, firstBuilding.lat],
    zoom: 15,
  })

  map.value.addControl(new maplibregl.NavigationControl(), 'top-right')

  buildings.forEach((building, idx) => {
    const markerElement = buildMarker(idx === 0 ? '#FF0000' : '#4C90CC')
    const popupContent = buildPopupContent(building)

    new maplibregl.Marker({ element: markerElement })
      .setLngLat([building.lon, building.lat])
      .setPopup(new maplibregl.Popup({ offset: 25 }).setDOMContent(popupContent))
      .addTo(map.value!)
  })
}

function destroyMap() {
  map.value?.remove()
  map.value = undefined
}

function buildMarker(color: string) {
  const element = document.createElement('div')
  element.className = 'custom-marker'
  element.style.backgroundColor = color
  element.style.width = '24px'
  element.style.height = '24px'
  element.style.borderRadius = '50% 50% 50% 0'
  element.style.transform = 'rotate(-45deg)'
  element.style.border = '2px solid white'
  element.style.boxShadow = '0 2px 4px rgba(0,0,0,0.3)'
  return element
}

function buildPopupContent(building: CampusBuilding) {
  const container = document.createElement('div')
  const title = document.createElement('strong')
  title.textContent = building.label
  container.appendChild(title)

  if (building.address) {
    container.appendChild(document.createElement('br'))
    container.appendChild(document.createTextNode(building.address))
  }

  return container
}

async function loadMapLibre() {
  if (!maplibreModulePromise) {
    maplibreModulePromise = Promise.all([
      import('maplibre-gl'),
      import('maplibre-gl/dist/maplibre-gl.css'),
    ]).then(([module]) => module)
  }

  return maplibreModulePromise
}
</script>

<template>
  <div class="campus-map-wrapper">
    <div class="campus-map-header">
      <h3>Campus location</h3>
      <p class="hint" v-if="!hasBuildings">
        No campus location available for this program.
      </p>
    </div>

    <!-- Maps container - side by side -->
    <div v-if="hasBuildings" class="maps-container">
      <!-- Interactive map on the left -->
      <div ref="mapContainer" class="map-container"></div>
      
      <!-- Static campus plan on the right -->
      <div class="static-plan-container" @click="openModal" title="Click to enlarge">
        <img 
          :src="campusPlanImage" 
          alt="HCW Campus Plan" 
          class="static-campus-plan"
        />
        <div class="zoom-hint">🔍 Click to enlarge</div>
      </div>
    </div>

    <!-- Modal for enlarged campus plan -->
    <div v-if="showModal" class="modal-overlay" @click="closeModal">
      <div class="modal-content">
        <button class="modal-close" @click="closeModal" aria-label="Close">×</button>
        <img 
          :src="campusPlanImage" 
          alt="HCW Campus Plan - Enlarged" 
          class="modal-image"
          @click.stop
        />
      </div>
    </div>

    <!-- Building information -->
    <div v-if="hasBuildings" class="campus-map-legend">
      <div class="legend-title">Buildings</div>
      <ul>
        <li v-for="b in buildings" :key="b.label + b.address">
          <strong>{{ b.label }}</strong> — {{ b.address }}
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.campus-map-wrapper {
  margin-top: 20px;
}

.campus-map-header h3 {
  margin: 0 0 6px 0;
}

.hint {
  margin: 0 0 10px 0;
  opacity: 0.8;
  font-size: 13px;
}

.maps-container {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
}

.map-container {
  flex: 1;
  height: 400px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.15);
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.static-plan-container {
  flex: 1;
  height: 400px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.15);
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  background-color: #f5f5f5;
  cursor: pointer;
  position: relative;
  transition: transform 0.2s, box-shadow 0.2s;
}

@media (max-width: 639px) {
  .maps-container {
    flex-direction: column;
    gap: 12px;
  }

  .map-container {
    height: 300px;
    width: 100%;
  }
  
  .static-plan-container {
    height: 300px;
    width: 100%;
  }
}

.static-plan-container:hover .zoom-hint {
  opacity: 1;
}

.static-campus-plan {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.zoom-hint {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
}

/* Modal styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  cursor: zoom-out;
  animation: fadeIn 0.2s;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  position: relative;
  max-width: 95vw;
  max-height: 95vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-image {
  max-width: 100%;
  max-height: 95vh;
  object-fit: contain;
  border-radius: 4px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  cursor: default;
}

.modal-close {
  position: absolute;
  top: -40px;
  right: 0;
  background: rgba(255, 255, 255, 0.9);
  border: none;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  color: #333;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.modal-close:hover {
  background: white;
  transform: scale(1.1);
}

.map-attribution a:hover {
  text-decoration: underline;
}

.campus-map-legend {
  margin-top: 10px;
  font-size: 13px;
}

.legend-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.campus-map-legend ul {
  margin: 0;
  padding-left: 18px;
}
</style>
