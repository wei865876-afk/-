<template>
  <router-view v-if="isPublic" />
  <AppLayout v-else>
    <router-view />
  </AppLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'

const route = useRoute()
const isPublic = computed(() => route.meta?.public === true)
</script>
