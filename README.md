# Ace 课表

网球教练排课工具，支持课程管理、每周重复排课、冲突检查、学员统计，以及带课前提醒的 ICS 日历文件导出。

这是无需构建的静态网站。将本目录发布到 GitHub Pages 即可使用。

课程与设置保存在当前浏览器的 localStorage 中，不会上传到 GitHub；不同设备不会自动同步。日历导出不等于自动同步，导入后请在手机日历核对提醒设置，并先创建测试课程确认提醒能够触达。

## 发布

仓库 Settings → Pages → Deploy from a branch → main → / (root) → Save。

## 文件

- index.html：页面
- styles.css：样式
- app.js：排课和日历导出
- sw.js：离线缓存
- manifest.webmanifest、icon.svg：主屏幕应用信息

