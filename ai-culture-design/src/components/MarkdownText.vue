<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdown.js'
const props = defineProps({ content: { type: String, default: '' } })
const html = computed(() => renderMarkdown(props.content))
</script>

<template><div class="markdown-text" v-html="html"></div></template>

<style scoped>
.markdown-text{white-space:normal;overflow-wrap:anywhere;line-height:1.85;font-size:14px;min-width:0}
.markdown-text :deep(p){margin:.65em 0}.markdown-text :deep(h1){font-size:1.5em}.markdown-text :deep(h2){font-size:1.3em}.markdown-text :deep(h3){font-size:1.15em}
.markdown-text :deep(h1),.markdown-text :deep(h2),.markdown-text :deep(h3){font-weight:650;line-height:1.5;margin:1.2em 0 .5em}
.markdown-text :deep(ul),.markdown-text :deep(ol){padding-left:1.6em;margin:.7em 0}.markdown-text :deep(li){margin:.3em 0}
.markdown-text :deep(a){color:#326658;text-decoration:underline;text-underline-offset:3px}
.markdown-text :deep(blockquote){border-left:3px solid #9aac98;padding-left:1em;margin:1em 0;color:#66776b}
.markdown-text :deep(pre){background:#edf0e7;border-radius:5px;padding:14px;overflow:auto;white-space:pre-wrap}
.markdown-text :deep(code){font-family:Consolas,monospace;font-size:.9em;background:#edf0e7;padding:.15em .3em}
.markdown-text :deep(table){display:block;max-width:100%;overflow-x:auto;border-collapse:collapse;margin:1em 0}
.markdown-text :deep(th),.markdown-text :deep(td){border:1px solid #dce1d4;text-align:left;padding:8px 12px;min-width:80px}
.markdown-text :deep(hr){border:0;border-top:1px solid #dce1d4;margin:1.3em 0}
</style>
