<template>
  <HomeDashboard v-if="isAuthenticated" />
  <HomeLanding v-else />
</template>

<script>
  import { mapGetters } from 'vuex'
  import HomeDashboard from './HomeDashboard.vue'
  import HomeLanding from './HomeLanding.vue'

  export default {
    name: 'Home',
    components: { HomeDashboard, HomeLanding },
    computed: {
      ...mapGetters(['isAuthenticated'])
    }
  }
</script>
