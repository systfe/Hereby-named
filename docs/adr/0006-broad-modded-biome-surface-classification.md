# 0006. 常见地形模组主世界地表群系全兼容与地下洞穴/异维度排除

## 状态

已接受（0.3.0）

## 背景

玩家在整合包（涵盖 Terralith、Biomes O' Plenty、Oh The Biomes We've Gone、Regions Unexplored、Nature's Spirit、Mystic's Biomes、Good Ending 等常见地形模组）游玩时，发现大量模组群系未被 `TerrainClassifier` 识别（归为 `OTHER`），无法参与自动地表区域的命名与识别。同时，此前名称选择存在按固定顺序遍历、导致同一类型地貌每次首选名字相同的出戏问题。

## 决策

1. **名称完全随机化**：在 [`AutoNames.java`](file:///src/main/java/dev/herebynamed/auto/AutoNames.java) 中，按类别和气候筛选候选默认名后使用 `Collections.shuffle` 打乱顺序，消除首选固定遍历问题。
2. **地下洞穴与异维度规范排除**：Xaero 地图与地貌命名针对主世界地表。地下洞穴（如原版 dripstone/lush caves、Terralith 地下洞穴、Yung's Cave Biomes、Galosphere 等）与异维度地下结构（Nether/End/Heaven 等）显式保留为 `OTHER`，防止在地表上方错误生成洞穴区域名。
3. **主世界地表特征词根全面扩充**：
   - **山地/耸峰/悬崖/峡谷/火山**（`MOUNTAIN`）：扩充 `cliffs`, `canyon`, `spires`, `slopes`, `hills`, `volcano`, `caldera`, `crater`, `stacks`, `barrera`, `bluff`, `skylands`, `highland` 等。
   - **森林/林间空地/果园/树丛/苍白之园/黄金树**（`FOREST`）：扩充 `glade`, `clearing`, `orchard`, `covert`, `boreal`, `tangle`, `garden`, `shield`, `tropics`, `erdtree`, `canopy`, `timber` 等。
   - **平原/湿地/沼泽/河谷/盆地/海滩**（`PLAIN`）：扩充 `swamp`, `swamplands`, `marsh`, `bog`, `fen`, `bayou`, `wetland`, `wetlands`, `floodplain`, `floodlands`, `valley`, `vale`, `basin`, `lowlands`, `delta`, `gour`, `brushland`, `bushland`, `chaparral`, `moor`, `wilds`, `beach`, `shore`, `shores`, `island`, `islands`, `isle`, `isles`, `yellowstone`。
   - **沙漠/干旱带/绿洲/台地**（`DESERT`）：扩充 `mesa`, `sands`, `oasis`, `dryland`, `drylands` 等。
   - **水系/湖泊/珊瑚礁/温泉/泻湖**（`LAKE` / `OCEAN`）：扩充 `lagoon`, `hot_springs`, `reef` 等。

## 影响

- 整合包中 485 个模组群系中，除 46 个地下洞穴与异维度群系按规范保持 `OTHER` 之外，所有常见地表群系识别率达到 100%。
