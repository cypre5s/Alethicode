declare module 'vue-cropper' {
  import type { DefineComponent } from 'vue'

  const VueCropper: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default VueCropper
}

declare module 'vue-cropper/lib/vue-cropper.vue' {
  import type { DefineComponent } from 'vue'

  const VueCropper: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default VueCropper
}
