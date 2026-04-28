<template>
  <router-view />
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  height: 100%;
}

:root {
  --el-color-primary: #b72a33;
  --el-color-primary-light-3: #cf4a52;
  --el-color-primary-light-5: #dc7b82;
  --el-color-primary-light-7: #edb4b8;
  --el-color-primary-light-8: #f4d1d4;
  --el-color-primary-light-9: #faeaeb;
  --el-color-primary-dark-2: #941f28;
  --el-border-radius-base: 4px;
}

.el-table th.el-table__cell {
  background: #f3d7d9 !important;
  color: #364152;
  font-weight: 600;
}

.el-button--primary {
  --el-button-bg-color: #b72a33;
  --el-button-border-color: #b72a33;
  --el-button-hover-bg-color: #cf4a52;
  --el-button-hover-border-color: #cf4a52;
  --el-button-active-bg-color: #941f28;
  --el-button-active-border-color: #941f28;
}
</style>
