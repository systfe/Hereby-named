# 此地名曰 (Hereby Named)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-brightgreen.svg)](https://minecraft.net/)
[![Mod Loaders](https://img.shields.io/badge/Mod%20Loader-Fabric%20%7C%20Forge%20%7C%20NeoForge-blue.svg)](https://github.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**此地名曰 (Hereby Named)** 是一款专为 **Xaero's World Map** 设计的跨平台 Minecraft 地理命名与领地管理模组（全端支持 **Fabric 1.20.1 / 1.21.1**、**Forge 1.20.1** 及 **NeoForge 1.21.1**）。

在广袤的方块世界中，你是否曾希望那些跨越数千格的雄伟山脉、浩瀚大洋、广袤原野与幽深森林拥有属于自己的名字？本模组能在 Xaero 世界地图中为地理区域提供**自动宏观地貌识别与命名**、**随地形曲率排版**、**手动多图层圈地绘制与区块常加载**、**踏足新领地时的电影级效果**，以及**多人联机共享与权限配置**。

---

## ✨ 核心特性

- 🌍 **探索地貌自动命名**
  - **群系斑块拓扑分析**：以宏观生物群系连通块为图单元，快速识别大洋、山脉、森林、平原、沙漠、北地冻土与封闭湖泊；
  - **山地动态演变**：山地随探索不断扩张，长条山地演化为“山脉”，广袤山体演化为“峦”，最多自动进阶改名一次；
  - **广泛模组群系兼容**：原生识别 Terralith、Biomes O' Plenty、Biomes We've Gone (BWG)、Regions Unexplored、Nature's Spirit、Mystic's Biomes、William Wythers' Overhauled Overworld (WWOO) 等。

- 📜 **欧陆风云 (EU4) 风格曲率自适应排版**
  - 地图名称文字自动根据地形走势拟合二次样条曲率曲线，单字符沿切线角度自然旋转排布；
  - 内置字符间距防重叠保护算法，即使在狭长垂直或多变地形下，字符之间依然保持严谨正向间距。

- 🔤 **高清汉字与可调透明度**
  - 内置精选商用开源高清字体 **思源宋体 (Noto Serif SC)** 与古典英文字体 **Cinzel**；
  - 支持游戏内**滑块实时拖拽调节文字不透明度**（默认 80%），文字自然沉浸融入地表纹理。

- 🎬 **电影级入界体验**
  - 踏入新领地时，屏幕上下平滑淡入宽银幕黑边（Letterbox），伴随独特音效；
  - 兼容 **Traveler's Titles** 模组，槽位发生冲突时智能让位，未安装时无缝启用内置平滑 HUD 渲染。

- 🏰 **多图层圈地与独立区块常加载 (Chunk Loader)**
  - 支持 256 个图层，分层管理领地；
  - 普通单击、`Shift + 拖动` 矩形框选、`Ctrl + 长按` 连续群系漫延选取；
  - 可对野外任意区块开启常加载，地图上清晰呈现青色边缘标记；
  - 丰富的撤销重做（`Ctrl+Z` / `Ctrl+Y`，保留 32 步历史）。

- ⚡ **多线程防卡死引擎**
  - 网络快照压缩与编解码由独立的 `Hereby Named sync` 线程调度，彻底杜绝大地图分析和磁盘 I/O 阻塞。

---

## 🎮 地图界面常用快捷操作

| 快捷操作 | 功能说明 |
| :--- | :--- |
| **点击左侧铅笔图标** | 进入 / 退出区域编辑模式 |
| **点击左侧漏斗图标** | 切换区块常加载 (Chunk Loader) 绘制工具 |
| **点击左侧齿轮图标** | 打开自动区域设置面板（门槛、全局参数、白名单、群系分类） |
| **普通左键 / 右键** | 单击将光标所在区块**添加进** / **移出**当前选中区域 |
| **Shift + 左键 / 右键拖拽** | 矩形框选批量**添加** / **移除**大片区块 |
| **Ctrl + 左键 / 右键长按 0.5s** | 自动向外漫延选取整片**相连的同种群系** |
| **Tab / Shift + Tab** | 快速在当前图层的所有区域之间循环切换选择 |
| **Ctrl + Z / Ctrl + Y** | 撤销（Undo）与重做（Redo）操作（最多保留 32 步历史） |
| **Esc 键** | 取消正在进行中的长按或矩形选择操作 |

---


## 📦 安装与版本支持

将对应平台与 Minecraft 版本的模组 jar 文件放入客户端（或服务器）的 `mods` 目录下即可。

| 模组端 / 平台 | 支持版本 | 必需依赖 | 推荐搭配 |
| :--- | :--- | :--- | :--- |
| **Fabric** | 1.20.1 / 1.21.1 | [Fabric API](https://curseforge.com/minecraft/mc-mods/fabric-api)<br>[Xaero's World Map](https://curseforge.com/minecraft/mc-mods/xaeros-world-map) | [Traveler's Titles](https://curseforge.com/minecraft/mc-mods/travelers-titles) |
| **Forge** | 1.20.1 | [Xaero's World Map](https://curseforge.com/minecraft/mc-mods/xaeros-world-map) | [Traveler's Titles](https://curseforge.com/minecraft/mc-mods/travelers-titles) |
| **NeoForge** | 1.21.1 | [Xaero's World Map](https://curseforge.com/minecraft/mc-mods/xaeros-world-map) | [Traveler's Titles](https://curseforge.com/minecraft/mc-mods/travelers-titles) |

---

## 📖 常用配置指南

### 1. 字体更换与透明度调节

#### 游戏内图形化配置（推荐）
1. 按 `M` 打开 Xaero's World Map 全屏地图；
2. 点击右上角设置图标中,选择 **“字体设置”**；
3. **字体选择**：
   - 点击 **“预设：思源宋体 / Cinzel (默认)”**：应用 `hereby_named:map_font`，呈现高清衬线效果；
   - 点击 **“预设：原版默认像素字体”**：切换回原版像素字体 `minecraft:default`；
   - 或在输入框中输入任意资源包或第三方模组的字体 ResourceLocation（例：`my_pack:custom_font`）；
4. **透明度调节**：直接拖拽 **“文字不透明度”滑块**（范围 0%～100%，默认 80%），拖动至满意的透明度后点击“保存并应用”。

#### 配置文件修改
配置文件位于 `.minecraft/config/hereby-named/automatic.json`：
```json
{
  "mapFont": "hereby_named:map_font",
  "mapFontOpacityPercent": 80
}
```

---

### 2. 领地扩张费用与常加载费用设置

服务器管理员或单人游戏开启作弊后，可直接在游戏聊天栏使用管理员指令设置费用，也可以修改服务器配置文件。

#### 游戏内指令配置
- **查询当前费用配置**：
  ```
  /claim config
  ```
- **设置圈地/扩张区块费用**（支持指定任意物品，缺省为绿宝石）：
  ```
  /claim config cost <数量> [物品ID]
  ```
  *示例 1（设置每圈一个区块消耗 50 个绿宝石）：*
  ```
  /claim config cost 50 minecraft:emerald
  ```
  *示例 2（设置每圈一个区块消耗 1 个下界合金锭）：*
  ```
  /claim config cost 1 minecraft:netherite_ingot
  ```
  *示例 3（设为免费）：*
  ```
  /claim config cost 0
  ```
- **设置常加载区块费用**：
  ```
  /claim config forceload_cost <数量> [物品ID]
  ```
  *示例（设置常加载一个区块消耗 64 个青金石）：*
  ```
  /claim config forceload_cost 64 minecraft:lapis_lazuli
  ```
- **重载配置文件**：
  ```
  /claim config reload
  ```

#### 配置文件修改
配置文件位于 `.minecraft/config/hereby-named/claim_config.json`（服务端或单人存档目录）：
```json
{
  "costPerChunk": 50,
  "costItem": "minecraft:emerald",
  "forceloadCostPerChunk": 0,
  "forceloadCostItem": "minecraft:emerald"
}
```

---

### 3. 区块常加载 (Chunk Loader) 使用方法

- **地图工具栏操作**：打开 Xaero 全屏地图，在左侧工具栏点击漏斗形状的“区块常加载”按钮，点击地图上任意区块即可切换常加载状态；
- **指令切换**：
  ```
  /claim forceload toggle <X坐标> <Z坐标>
  ```
  被常加载的区块在地图上会以青色边框（`#00BCD4`）高亮标识，并在服务端自动持久化保存于 `chunk_loaders.json`。

---

### 4. 视听沉浸感配置（宽银幕黑边与入界音效）

首次运行后会自动在 `.minecraft/config/hereby-named/` 下生成配置文件：
- **`title-sound.json`**：
  - `enabled`: 是否启用踏入区域音效（默认 `true`）；
  - `category`: 音效通道（默认 `"ambient"`）；
  - `layers`: 音效合成层（默认合成羊皮纸翻页声与地图师羽毛笔手绘声，支持自由添加原版或模组音效 ID 与音量/音调/延迟）；
  - `pitchJitter`: 随机微小音调偏移，防止重复穿梭显得机械化。
- **`title-visuals.json`**：
  - `letterboxPercent`: 宽银幕黑边屏幕占比（默认 `8.0%`）；
  - `letterboxMs`: 黑边滑入滑出耗时（默认 `1300ms`）；
  - `curve`: 缓动曲线类型（`"brake"`、`"cubic"`、`"quartic"`）；
  - `repeatCooldownMs`: 同一区域重复进入防刷屏冷却时间（默认 `15000ms`）；
  - `switchCooldownMs`: 相邻区域边界横跳防刷屏冷却时间（默认 `5000ms`）。

---

## 🏗️ 从源码构建

本项目采用多架构（Multi-Loader：Common + Fabric + Forge + NeoForge）Gradle 构建系统：

```powershell
# 运行单元测试
./gradlew.bat :common:test

# 单独构建指定平台
./gradlew.bat -Pminecraft_version=1.20.1 :fabric:remapJar
./gradlew.bat -Pminecraft_version=1.21.1 :fabric:remapJar
./gradlew.bat :forge:jar
./gradlew.bat :neoforge:jar

# 自动化全平台发布构建（输出至 dist/ 目录）
powershell -ExecutionPolicy Bypass -File ./scripts/export-release.ps1 -Version 0.4.0
```

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 协议开源。
欢迎提交 Issue 报告缺陷或发起 Pull Request 贡献代码！
