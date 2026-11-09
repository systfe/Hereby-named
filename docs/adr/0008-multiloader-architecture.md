# 0008. 多加载器工程架构与零额外运行时依赖

0.3.1 跨平台移植采用轻量 MultiLoader 模块化架构，将领域模型、地貌分析、界面与通用网络分片统一收敛至基于 Mojang 官方映射的 Common 共享模块；根据各版本主流生态仅维护 1.20.1（Fabric + Forge）与 1.21.1（Fabric + NeoForge）共 4 个安装目标；坚持零额外运行时依赖（Zero Runtime Dependencies）原则，通过极简的 PlatformHelper 接口抽象与各平台原生事件/网络总线对接，拒绝强行引入 Architectury API 等重型玩家端前置。
