<script setup lang="ts">
import PageScaffold from '../../components/common/PageScaffold.vue'
import { PAGE_PLANS } from '../../constants/pagePlans'

const plan = PAGE_PLANS.assignment
</script>

<template>
  <PageScaffold :plan="plan" />
</template>
