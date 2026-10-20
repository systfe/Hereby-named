# Xaero的世界地图 | 此地名曰

**Xaero's World Map | Hereby Named**，Fabric 附属模组，标识 `hereby_named`。

在 Xaero 世界地图中给区块组成的地区命名，用图层区分高原、城市、建筑等范围；支持多人共享和进入区域标题。完整规则见 [需求规格](docs/requirements.md)。

## 安装

将 `build/libs/hereby-named-0.1.0+1.20.1.jar` 放入客户端 `mods`，与 Fabric API、Xaero's World Map 一同使用。支持的首个版本组合为 Minecraft 1.20.1、Java 17、Fabric Loader 0.17.2、Fabric API 0.92.6+1.20.1、Xaero World Map 1.47.0。

多人共享时，服务器也安装此地名曰与 Fabric API；服务器无需安装 Xaero World Map、Traveler's Titles 或任何客户端模组。未装服务器端时自动使用本地命名。其他玩家也需安装客户端才会看到命名。

Traveler's Titles 1.20-Fabric-4.0.2 为可选兼容，并使用它自己的正常依赖。没有它时使用内置区域标题。Minecraft 1.20、1.21.1 及其他 Xaero 版本尚未声明兼容，不应直接使用此 jar。

## 操作

- 打开 Xaero 世界地图，左侧点击“切换区域编辑模式”；选择图层，新增选区并填写名称、颜色。
- 地图入口与操作使用 Xaero 同款透明图标按钮，悬停查看功能。左侧叠层图标切换显示，边界与铅笔图标进入编辑；顶部依次为区域选择、新增／改名／删除区域、图层选择、新增／删除图层、显示切换和帮助。区域选择框左侧色条表示当前标记颜色。
- 普通左键单击加入区块，右键移除当前区域的区块；左键拖动保留地图平移。
- Shift + 左／右键拖动矩形选择／移除；Ctrl + 左／右键在原地按住约 0.5 秒，选择／移除连续群系。群系按区块中心地表判定，仅边相邻连通，只处理已记录地图。
- Tab 切换本层区域，Shift+Tab 反向切换。区域和图层下拉框可直接选择；较长列表可点击“下一页”。
- “改名/颜色”修改当前区域；区域创建后不能迁移图层。删除图层会一并删除该层区域，操作前弹窗确认。
- Ctrl+Z 撤销，Ctrl+Y 或 Ctrl+Shift+Z 重做。退出编辑清空历史；保留最近最多 32 步（还受历史内存预算限制）。Esc 可取消尚未完成的批量选择。
- 图层显示按钮只过滤颜色覆盖，不影响地图顶部区域文本或进入区域标题。

地图区域文本固定在顶部原有群系文字下面，显示鼠标所在区块的最小／最大图层区域名。游玩时地区稳定 1 秒后触发标题，等待 Traveler's Titles 原有维度、群系标题结束，两行分别套用其维度、群系样式。

## 多人权限与数据

所有安装客户端的玩家可查看；OP、单人世界主人及获授权者可编辑。同一维度同时只允许一人编辑，退出地图、退出编辑或断线时释放。

OP 或控制台使用 `/herebynamed grant 在线玩家名` 授权，`/herebynamed revoke 在线玩家名` 撤销。授权按 UUID 保存于世界目录 `hereby-named/editors.txt`。

共享区域位于世界目录 `hereby-named/regions/`，本地命名位于实例目录 `hereby-named/local/`，按世界或服务器身份及维度隔离。服务器后来启用共享时不会覆盖本地文件，也不会自动上传或合并。备份这些目录即可备份命名。正常保存使用临时文件替换，并保留上一版 `.bak`；损坏存档会报错，禁止以空白数据悄悄覆盖。

每维度最多 256 个图层、1024 个区域、500000 个区域区块；每次批量操作最多 65536 个区块。超限批量操作会取消整次选择，不会悄悄截取部分群系。

## 构建与测试

使用 JDK 17：

```powershell
./scripts/prepare-dependencies.ps1 -MapJar '完整路径/xaeroworldmap-fabric-1.20.1-1.47.0.jar'
./gradlew.bat build
```

工程紧邻当前实例 `mods` 时，准备脚本可省略参数。Xaero 与嵌入的 XaeroLib 只作编译依赖，不随本项目再分发。Gradle Wrapper 下载 Gradle 8.12.1，首次构建还需下载 Fabric/Minecraft 依赖。

测试覆盖区域覆盖／撤销、图层编号、首尾名称、群系连通和异步等待、标题排队、权限与维度锁、存档及网络分片。运行单个测试示例：`./gradlew.bat test --tests dev.herebynamed.domain.RegionBookTest`。

实现与手工验证记录见 [验证记录](docs/verification.md)。
