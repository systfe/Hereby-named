# Xaero的世界地图 | 此地名曰

**Xaero's World Map | Hereby Named**，Fabric 附属模组: `hereby_named`。

在 Xaero 世界地图中给区块组成的地区命名，用图层区分高原、城市、建筑等范围；支持多人共享和进入区域标题。完整规则见 [需求规格](docs/requirements.md)。

## 安装

选择对应 Minecraft 版本的 jar 放入客户端 `mods`，与 Fabric API、Xaero's World Map 一同使用；同一实例只保留一个此地名曰 jar。

| Minecraft | 安装包 | 最低 Java | 构建及兼容目标 |
| --- | --- | --- | --- |
| 1.20.1 | `build/1.20.1/libs/hereby-named-0.2.0+1.20.1.jar` | 17 | Fabric Loader 0.17.2、Fabric API 0.92.6+1.20.1、Xaero World Map 1.47.0、Traveler's Titles 4.0.2 |
| 1.21.1 | `build/1.21.1/libs/hereby-named-0.2.0+1.21.1.jar` | 21 | Fabric Loader 0.19.3、Fabric API 0.116.13+1.21.1、Xaero World Map 1.44.2、Traveler's Titles 5.1.3 |

多人共享时，服务器也安装此地名曰与 Fabric API；服务器无需安装 Xaero World Map、Traveler's Titles 或任何客户端模组。未装服务器端时自动使用本地命名。其他玩家也需安装客户端才会看到命名。

Traveler's Titles 为可选兼容，并使用它自己的正常依赖。没有它时使用内置区域标题。Minecraft 1.20 及其他 Xaero 版本尚未声明兼容。部分整合包中的其他模组可能要求更高 Java 版本。

## 操作

- 打开 Xaero 世界地图，左侧点击“切换区域编辑模式”；选择图层，新增选区并填写名称、颜色。
- 地图入口与操作使用 Xaero 同款透明图标按钮，悬停查看功能。左侧叠层图标切换显示，边界与铅笔图标进入编辑；顶部依次为区域选择、新增／改名／删除区域、图层选择、新增／删除图层、显示切换和帮助。区域选择框左侧色条表示当前标记颜色。
- 普通左键单击加入区块，右键移除当前区域的区块；左键拖动保留地图平移。
- Shift + 左／右键拖动矩形选择／移除；Ctrl + 左／右键在原地按住约 0.5 秒，选择／移除连续群系。群系按区块中心地表判定，仅边相邻连通，只处理已记录地图。
- Tab 切换本层区域，Shift+Tab 反向切换。区域和图层下拉框可直接选择；较长列表可点击“下一页”。
- “改名/颜色”修改当前区域；区域创建后不能迁移图层。删除图层会一并删除该层区域，操作前弹窗确认。
- 颜色直接点击常用色板，或拖动右侧彩虹条选色相，再在左侧调色区选择饱和度与明暗；上方色块实时预览。修改已有区域时默认保留原色。
- Ctrl+Z 撤销，Ctrl+Y 或 Ctrl+Shift+Z 重做。退出编辑清空历史；保留最近最多 32 步（还受历史内存预算限制）。Esc 可取消尚未完成的批量选择。
- 图层显示按钮只过滤颜色覆盖，不影响地图顶部区域文本或进入区域标题。
- 区域以连续半透明底色、外边界和孔洞边界显示，内部不绘制区块网格。地区名称显示在可见区域中心附近，随区域面积和缩放调整大小；过小的文字隐藏，地图顶部名称始终可查看。
- Ctrl + 左键长按群系选择涉及同图层其他区域时，先选择“不覆盖其他区域”或“覆盖其他区域”；Esc 取消本次选择。不同图层叠加不触发覆盖提示。普通单击及 Shift 矩形仍直接按原规则修改。

地图区域文本固定在顶部原有群系文字下面，显示鼠标所在区块的最小／最大图层区域名。游玩时地区稳定 1 秒后触发标题，等待 Traveler's Titles 原有维度、群系标题结束，两行分别套用其维度、群系样式。

## 多人权限与数据

所有安装客户端的玩家可查看；OP、单人世界主人及获授权者可编辑。同一维度同时只允许一人编辑，退出地图、退出编辑或断线时释放。

OP 或控制台使用 `/herebynamed grant 在线玩家名` 授权，`/herebynamed revoke 在线玩家名` 撤销。授权按 UUID 保存于世界目录 `hereby-named/editors.txt`。

共享区域位于世界目录 `hereby-named/regions/`，本地命名位于实例目录 `hereby-named/local/`，按世界或服务器身份及维度隔离。服务器后来启用共享时不会覆盖本地文件，也不会自动上传或合并。备份这些目录即可备份命名。正常保存使用临时文件替换，并保留上一版 `.bak`；损坏存档会报错，禁止以空白数据悄悄覆盖。

每维度最多 256 个图层、1024 个区域、500000 个区域区块；每次批量操作最多 65536 个区块。超限批量操作会取消整次选择，不会悄悄截取部分群系。

## 构建与测试

在源码工程根目录执行以下命令。两版共用领域逻辑、界面与存档格式；`src/platform/版本/java` 隔离 Fabric 网络与 HUD API 差异。构建 1.21.1 使用 JDK 21；同一个 JDK 21 也可构建以 Java 17 为目标的 1.20.1 版本。

```powershell
./scripts/prepare-dependencies.ps1 -MapJar '完整路径/xaeroworldmap-fabric-1.20.1-1.47.0.jar'
./gradlew.bat build

./scripts/prepare-dependencies.ps1 -MinecraftVersion 1.21.1 -MapJar '完整路径/xaeroworldmap-fabric-1.21.1-1.44.2.jar'
./gradlew.bat -Pminecraft_version=1.21.1 build
```

准备脚本也接受 `-InstancePath 实例目录`，自动从其 `mods` 寻找对应版本。Xaero 与嵌入的 XaeroLib 只作编译依赖，不随本项目再分发。Gradle Wrapper 下载 Gradle 8.12.1，首次构建还需下载 Fabric/Minecraft 依赖。

每个版本均导出两个安装包：配置 JDK 21 后执行 `./scripts/export-release.ps1 -Version 0.2.0`，脚本构建并测试 1.20.1 和 1.21.1，全部通过后将两个 jar 复制到 `dist/`。

0.1.0 起使用 `regions_v2` 通信通道；0.2.0 新增“不覆盖”编辑操作，多人共享需要服务器和客户端一同更新。已有区域文件格式保持兼容。可通过 `-Pmod_version=版本号` 指定构建版本。


测试覆盖区域覆盖／撤销、图层编号、首尾名称、群系连通和异步等待、标题排队、权限与维度锁、存档及网络分片。运行单个测试示例：`./gradlew.bat test --tests dev.herebynamed.domain.RegionBookTest`。

本轮验证与审查见 [0.2.0 验证记录](docs/verification-0.2.0.md) 和 [审查记录](docs/review-0.2.0.md)；早期实现见 [首版验证](docs/verification.md)。
