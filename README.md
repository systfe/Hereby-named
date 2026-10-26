# 此地名曰 (Hereby Named)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-brightgreen.svg)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> *“天地苍茫，行至此境，此地名曰——”*

**此地名曰 (Hereby Named)** 是一款专为 **Xaero's World Map** 设计的 Minecraft Fabric 附属模组。

在广袤的方块世界中，你是否曾希望那些跨越数千格的雄伟山脉、浩瀚大洋、广袤原野与幽深森林拥有属于自己的名字？本模组能在 Xaero 世界地图中为地理区域提供**自动宏观识别与命名**、**手动多图层圈地绘制**、**踏足新领地时的沉浸式大标题**，以及**多人联机共享与权限管理**。

---

## ✨ 核心特性

- 🌍 **探索地貌自动命名**
  - **群系斑块拓扑分析**：以宏观生物群系连通块为图单元，快速识别大洋、山脉、森林、平原、沙漠、北地冻土与封闭湖泊；
  - **自然地理过渡与包容**：微小杂乱群系斑块自动被周围主导地貌吞没，消除地图孔洞,保证地区完整；
  - **山地动态演变**：山地随探索不断扩张，长条山地演化为“山脉”，广袤山体演化为“峦”，最多自动进阶改名一次。
  - **群系支持**：已支持识别以下模组中的群系:

   Terralith、Biomes O' Plenty、Biomes We've Gone(BWG)、RegionsUnexplored、Nature's Spirit、Mystic's Biomes、William Wythers' Overhauled Overworld (WWOO) 


- 📜 **命名库**
  - **随机默认名**：可自定义命名池；
  - **单字词根随机组合**：基于数十个自然字素（寒、霜、月、风、龙、星、岩、林等），结合地形与冷暖气候偏好自然衍生组合命名；

- 🎨 **强大直观的地图编辑体验**

![](docs\编辑和使用界面.png)
  - **多图层体系**：支持 256 个图层，分层管理地区；
  - **多种选取工具**：普通单区块点击、`Shift + 拖动` 矩形大选区、`Ctrl + 长按` 快速选取同种连续群系；
  - **全功能调色盘与撤销**：RGB 色轮与饱和度面板、常用色板、实时预览以及 `Ctrl+Z` / `Ctrl+Y` 多步历史撤销；
  - **地图无缝矢量呈现**：平滑半透明渲染，外边缘与孔洞自动描边，区域名称居中并随缩放智能调整字号。

- 🏰 **跨入领地沉浸式标题**
  - 支持兼容 **Traveler's Titles** 模组,行至新区域稳定 1 秒后触发进入标题提示；未安装时自动使用内置平滑 HUD 渲染。

- ⚙️ **设置界面**

![](docs\设置界面.png)
  - **全群系分类浏览器**：动态扫描游戏已加载的所有生物群系，支持按 ID 快速搜索、按分类过滤、点击循环修改其地形分类；
  - **增量覆盖持久化**：仅保存玩家自定义修改的群系映射，保持模组版本更新的长期兼容性。

- 👥 **服务端原生支持与权限控制**
  - 服务端安装后多人联机实时共享区域数据，客户端安装后即可看到；
  - 拥有严格的并发编辑锁与 OP / 指令授权体系（`/herebynamed grant/revoke <玩家名>`），保护玩家建筑与领地不被误改。

---

## 📦 安装与前置要求

将对应 Minecraft 版本的模组 jar 文件放入客户端（或服务器）的 `mods` 目录下即可。

| Minecraft 版本 | 必需前置模组 | 推荐/可选模组 |
| :--- | :--- | :--- |
| **1.20.1** | [Fabric Loader](https://fabricmc.net/) $\ge 0.16.0$<br>[Fabric API](https://curseforge.com/minecraft/mc-mods/fabric-api)<br>[Xaero's World Map](https://curseforge.com/minecraft/mc-mods/xaeros-world-map) $\ge 1.47.0$ | [Traveler's Titles](https://curseforge.com/minecraft/mc-mods/travelers-titles) 4.0.2 |
| **1.21.1** | [Fabric Loader](https://fabricmc.net/) $\ge 0.16.0$<br>[Fabric API](https://curseforge.com/minecraft/mc-mods/fabric-api)<br>[Xaero's World Map](https://curseforge.com/minecraft/mc-mods/xaeros-world-map) $\ge 1.44.2$ | [Traveler's Titles](https://curseforge.com/minecraft/mc-mods/travelers-titles) 5.1.3 |

> [!TIP]
> **多人联机说明**：
> - 仅需在服务器上安装 `hereby_named` 和 `Fabric API`，服务器**无需**安装 Xaero's World Map 或任何客户端渲染模组。
> - 未安装服务端的单人或多人模式下，会自动使用客户端本地存档命名。

---

## 🎮 操作与快捷键指南

打开 Xaero's World Map 全屏地图界面：

| 快捷操作 | 功能说明 |
| :--- | :--- |
| **点击左侧铅笔图标** | 进入 / 退出区域编辑模式 |
| **点击左侧齿轮图标** | 打开自动区域设置面板（门槛、全局参数、白名单、群系分类） |
| **普通左键 / 右键** | 单击将光标所在区块**添加进** / **移出**当前选中区域 |
| **Shift + 左键 / 右键拖拽** | 矩形框选批量**添加** / **移除**大片区块 |
| **Ctrl + 左键 / 右键长按 0.5s** | 自动向外漫延选取整片**相连的同种群系** |
| **Tab / Shift + Tab** | 快速在当前图层的所有区域之间循环切换选择 |
| **Ctrl + Z / Ctrl + Y** | 撤销（Undo）与重做（Redo）操作（最多保留 32 步历史） |
| **Esc 键** | 取消正在进行中的长按或矩形选择操作 |

---

## 🛠️ 高级配置与自定义

绝大多数设置均可在游戏内直接点击齿轮完成。对于整合包作者或服务器管理员，配置文件保存在 `.minecraft/config/hereby-named/` 目录下：

- **`automatic.json`**：各类地貌最低跨度门槛、连续探索解锁距离、分析间隔、删除保护开关，以及玩家自定义的群系分类映射（`biomeOverrides`）；
- **`names.json`**：默认名白名单（支持配置适用的地形类别与冷暖气候限制）；
- **`roots.json`**：词根白名单（支持配置各字素的独立地形与气候倾向）。

---

## 🏗️ 从源码构建

本项目采用标准 Gradle 构建系统，双版本共用领域逻辑与架构代码：

```powershell
# 准备依赖（指定 Xaero 地图 Jar 路径）
./scripts/prepare-dependencies.ps1 -MapJar "路径/xaeroworldmap-fabric-1.20.1.jar"

# 构建 1.20.1 版本
./gradlew.bat build

# 构建 1.21.1 版本（需 JDK 21）
./gradlew.bat -Pminecraft_version=1.21.1 build

# 自动化全量发布导出（自动构建并测试 1.20.1 与 1.21.1，输出至 dist/）
./scripts/export-release.ps1 -Version 0.3.1
```

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 协议开源。
欢迎提交 Issue 报告缺陷或发起 Pull Request 贡献代码！
