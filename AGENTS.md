# 项目约定

- 自 0.3.1 版本起，每个发布版本构建并导出 4 个安装包：Fabric 1.20.1、Forge 1.20.1、Fabric 1.21.1 和 NeoForge 1.21.1，使用 `scripts/export-release.ps1 -Version 版本号`，完成后报告 4 个包的位置与 SHA-256。
- Git 提交的作者和提交者使用本机 Git 配置中的姓名、邮箱；提交前确认仓库配置与本机全局配置一致。
- 源码公开上传前遵循 `docs/privacy-audit.md`，将当前源码快照与包含旧私人信息的 Git 历史区分说明。
