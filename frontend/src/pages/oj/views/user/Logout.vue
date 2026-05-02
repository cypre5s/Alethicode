<template>
  <div class="logout-page" aria-hidden="true"></div>
</template>

<script>
  import api from '../../api.js'

  export default {
    name: 'Logout',
    mounted () {
      api.logout().then(res => {
        this.$store.dispatch('clearProfile')
        this.$router.replace({
          path: '/'
        })
      })
    }
  }
</script>
