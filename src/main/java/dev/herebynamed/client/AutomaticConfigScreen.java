package dev.herebynamed.client;

import dev.herebynamed.auto.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.IntConsumer;

/** One isolated settings draft shared by the forms and list editors. */
final class AutomaticConfigScreen extends Screen implements EditorDialog {
    private static final TerrainKind[] KINDS={TerrainKind.MOUNTAIN,TerrainKind.OCEAN,TerrainKind.FROZEN_OCEAN,
            TerrainKind.PLAIN,TerrainKind.FOREST,TerrainKind.FLOWER,TerrainKind.DESERT,TerrainKind.LAKE};
    private static final String[] LABELS={"山地","大洋","冻洋","原野","森林","花草甸","沙漠","湖泊"};
    private final Screen parent;
    private final AutomaticRegions automatic;
    private final AutoConfig draft;
    private final Map<String,String> rawFields=new LinkedHashMap<>();
    private final Map<String,IntConsumer> fieldWriters=new LinkedHashMap<>();
    private final List<String> rowLabels=new ArrayList<>();
    private int tab,page,x,contentWidth,rows;
    private boolean saving;
    private String error="";

    AutomaticConfigScreen(Screen parent,AutomaticRegions automatic,int tab) {
        super(Text.literal("自动区域设置"));this.parent=parent;this.automatic=automatic;this.tab=tab;draft=automatic.settingsDraft();
    }
    private ButtonWidget button(int bx,int by,int w,String text,Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(text),b -> action.run()).dimensions(bx,by,w,20).build());
    }
    static String label(TerrainKind kind) {
        for(int i=0;i<KINDS.length;i++) if(KINDS[i]==kind) return LABELS[i];
        return kind==TerrainKind.RIVER?"河流":"暂不分类";
    }
    @Override protected void init() {
        rowLabels.clear();contentWidth=Math.min(350,width-20);x=(width-contentWidth)/2;
        rows=Math.max(1,Math.min(10,(height-140)/24));page=Math.min(page,Math.max(0,(count()-1)/rows));
        String[] tabs={"区域门槛","全局参数","完整名字","词根","群系规则"};
        int tabWidth=(contentWidth-8)/5;
        for(int i=0;i<tabs.length;i++) { final int index=i;
            button(x+i*(tabWidth+2),26,tabWidth,tabs[i],() -> navigate(index,0)).active=tab!=i;
        }
        if(tab==0) thresholds();else if(tab==1) globals();else lists();
        int pages=Math.max(1,(count()+rows-1)/rows);
        button(x,height-54,62,"上一页",() -> navigate(tab,page-1)).active=page>0;
        button(x+contentWidth-62,height-54,62,"下一页",() -> navigate(tab,page+1)).active=page+1<pages;
        button(x,height-28,(contentWidth-6)/2,"保存并应用",this::save);
        button(x+(contentWidth+6)/2,height-28,(contentWidth-6)/2,"取消",this::close);
        if(saving) for(var child:children()) if(child instanceof ClickableWidget widget) widget.active=false;
    }
    private int count() { return tab==0?8:tab==1?9:tab==4?draft.biomeRules.size():names().size(); }
    private List<AutoConfig.Name> names() { return tab==3?draft.roots:draft.names; }
    private void navigate(int nextTab,int nextPage) {
        tab=nextTab;page=Math.max(0,nextPage);error="";clearAndInit();
    }
    private boolean read() {
        try {
            Map<String,Integer> values=new LinkedHashMap<>();
            rawFields.forEach((key,value) -> values.put(key,Integer.parseInt(value)));
            values.forEach((key,value) -> fieldWriters.get(key).accept(value));
            error="";return true;
        }
        catch(IllegalArgumentException ex) { error="请输入有效的非负整数";return false; }
    }
    private TextFieldWidget number(int bx,int by,int w,int value,String hint,String key,IntConsumer writer) {
        TextFieldWidget input=new TextFieldWidget(textRenderer,bx,by,w,18,Text.literal(hint));
        fieldWriters.put(key,writer);rawFields.putIfAbsent(key,Integer.toString(value));
        input.setMaxLength(7);input.setTextPredicate(s -> s.matches("[0-9]*"));input.setText(rawFields.get(key));
        input.setChangedListener(text -> rawFields.put(key,text));
        input.setTooltip(Tooltip.of(Text.literal(hint)));addDrawableChild(input);return input;
    }
    private void thresholds() {
        for(int index=page*rows;index<Math.min(8,(page+1)*rows);index++) {
            TerrainKind kind=KINDS[index];AutoConfig.Threshold value=threshold(kind);int y=74+(index-page*rows)*24;
            int w=(contentWidth-90)/3;
            number(x+90,y,w-5,value.minimumBiomes(),"不同群系种数；0 停用。与最低跨度满足其一即可",kind+":biomes",
                    v -> { var t=threshold(kind);setThreshold(kind,new AutoConfig.Threshold(v,t.minimumSpan(),t.maximumSpan())); });
            number(x+90+w,y,w-5,value.minimumSpan(),"最低跨度，单位为方块；0 停用此分支",kind+":span",
                    v -> { var t=threshold(kind);setThreshold(kind,new AutoConfig.Threshold(t.minimumBiomes(),v,t.maximumSpan())); });
            number(x+90+2*w,y,w-5,value.maximumSpan(),"跨度必须小于此值；0 表示没有上限",kind+":maximum",
                    v -> { var t=threshold(kind);setThreshold(kind,new AutoConfig.Threshold(t.minimumBiomes(),t.minimumSpan(),v)); });
            rowLabels.add(label(kind));
        }
    }
    private AutoConfig.Threshold threshold(TerrainKind kind) {
        return switch(kind) {
            case MOUNTAIN -> draft.thresholds.mountain;case OCEAN -> draft.thresholds.ocean;case FROZEN_OCEAN -> draft.thresholds.frozenOcean;
            case PLAIN -> draft.thresholds.plain;case FOREST -> draft.thresholds.forest;case FLOWER -> draft.thresholds.flower;
            case DESERT -> draft.thresholds.desert;case LAKE -> draft.thresholds.lake;default -> throw new IllegalArgumentException();
        };
    }
    private void setThreshold(TerrainKind kind,AutoConfig.Threshold value) {
        switch(kind) {
            case MOUNTAIN -> draft.thresholds.mountain=value;case OCEAN -> draft.thresholds.ocean=value;case FROZEN_OCEAN -> draft.thresholds.frozenOcean=value;
            case PLAIN -> draft.thresholds.plain=value;case FOREST -> draft.thresholds.forest=value;case FLOWER -> draft.thresholds.flower=value;
            case DESERT -> draft.thresholds.desert=value;case LAKE -> draft.thresholds.lake=value;default -> throw new IllegalArgumentException();
        }
    }
    private void globals() {
        String[] labels={"启用所需连续跨度","所有区域最小跨度","每种群系最少区块数","地表缓存区块上限","分析间隔（秒）","山脉／广域山地跨度","广域山地最少区块数","山岭长宽比（百分比）",""};
        String[] hints={"256～1000000 格；已记录地表须连成一片","16～100000 格；对所有类别生效","1～4096；过小的群系碎片不计入种数","4096～500000；更高上限会占用更多内存","10～600 秒；更频繁分析会增加开销","16～1000000 格；长条山地达到此跨度用山脉，宽广山地用峦","1～500000 个区块；宽广山地达到此面积用峦","110～1000；180 表示长边大于短边的 1.8 倍时视为绵延山地",""};
        int[] values={draft.unlockSpan,draft.minimumSpan,draft.minimumBiomeChunks,draft.maximumSamples,draft.analysisIntervalSeconds,draft.mountainRangeSpan,draft.mountainMassifChunks,draft.mountainRidgeRatioPercent};
        IntConsumer[] setters={v -> draft.unlockSpan=v,v -> draft.minimumSpan=v,v -> draft.minimumBiomeChunks=v,v -> draft.maximumSamples=v,v -> draft.analysisIntervalSeconds=v,v -> draft.mountainRangeSpan=v,v -> draft.mountainMassifChunks=v,v -> draft.mountainRidgeRatioPercent=v};
        for(int index=page*rows;index<Math.min(9,(page+1)*rows);index++) {
            int y=74+(index-page*rows)*24;
            if(index==8) { rowLabels.add("自动创建区域");button(x+contentWidth-96,y,90,draft.enabled?"开启":"关闭",() -> { draft.enabled=!draft.enabled;clearAndInit(); });continue; }
            rowLabels.add(labels[index]);number(x+contentWidth-96,y,90,values[index],hints[index],"global:"+index,setters[index]);
        }
    }
    private void lists() {
        boolean rules=tab==4;
        button(x+contentWidth-58,51,58,"新增",() -> openEntry(-1));
        if(!rules) button(x+contentWidth-126,51,64,"恢复默认",() -> client.setScreen(new Confirm(this,"用默认名称替换当前草稿中的白名单？",() -> {
            try { if(tab==3) draft.roots=AutoConfig.defaultNames(true);else draft.names=AutoConfig.defaultNames(false);error=""; }
            catch(java.io.IOException ex) { error="读取默认名单失败"; }
            page=0;
        })));
        for(int index=page*rows;index<Math.min(count(),(page+1)*rows);index++) {
            int y=74+(index-page*rows)*24,i=index;
            rowLabels.add(rules?draft.biomeRules.get(i).biome():names().get(i).text());
            button(x+contentWidth-86,y,40,"编辑",() -> openEntry(i));
            button(x+contentWidth-42,y,40,"删除",() -> client.setScreen(new Confirm(this,"从草稿中删除“"+rowLabels.get(i-page*rows)+"”？",() -> {
                if(rules) draft.biomeRules.remove(i);else names().remove(i);
            })));
        }
    }
    private void openEntry(int index) { client.setScreen(new EntryScreen(index,tab==4)); }
    private void save() {
        if(saving || !read()) return;
        try { draft.validate(); } catch(IllegalArgumentException ex) { error=ex.getMessage();return; }
        saving=true;clearAndInit();
        var connection=client.getNetworkHandler();var world=client.world;
        automatic.saveSettings(draft,message -> {
            saving=false;
            if(client.currentScreen!=this || client.getNetworkHandler()!=connection || client.world!=world) return;
            if(message.isEmpty()) client.setScreen(parent);else { error=message;clearAndInit(); }
        });
    }
    @Override public void close() { if(!saving) client.setScreen(parent); }
    @Override public void render(DrawContext context,int mx,int my,float delta) {
        ClientUi.background(this,context,mx,my,delta);super.render(context,mx,my,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,8,0xffffff);
        if(tab==0) {
            context.drawTextWithShadow(textRenderer,"类别",x,57,0xbbbbbb);int w=(contentWidth-90)/3;
            String[] headers={"群系种数","最低跨度","跨度上限"};
            for(int i=0;i<3;i++) context.drawTextWithShadow(textRenderer,headers[i],x+90+i*w,57,0xbbbbbb);
        } else context.drawTextWithShadow(textRenderer,tab==1?"悬停输入框查看范围":"共 "+count()+" 条",x,57,0xbbbbbb);
        for(int i=0;i<rowLabels.size();i++) context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(rowLabels.get(i),tab==0?82:contentWidth-102),x,79+i*24,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,(page+1)+" / "+Math.max(1,(count()+rows-1)/rows),width/2,height-48,0xbbbbbb);
        String message=saving?"正在保存…":error.isEmpty()?"保存后生效；取消不会修改现有设置":error;
        context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(message,contentWidth),width/2,height-68,error.isEmpty()?0xaaaaaa:0xff8888);
    }
    private static final class Confirm extends ConfirmScreen implements EditorDialog {
        Confirm(AutomaticConfigScreen parent,String question,Runnable action) {
            super(yes -> { if(yes) action.run();net.minecraft.client.MinecraftClient.getInstance().setScreen(parent); },Text.literal("确认操作"),Text.literal(question),Text.literal("确认"),Text.literal("取消"));
        }
    }
    private final class EntryScreen extends Screen implements EditorDialog {
        private final int index;
        private final boolean rule;
        private final Set<String> kinds=new LinkedHashSet<>();
        private String climate="ANY",entryError="";
        private TerrainKind ruleKind=TerrainKind.PLAIN;
        private TextFieldWidget text;
        private String value;
        private int left,top,w;
        EntryScreen(int index,boolean rule) {
            super(Text.literal(rule?"群系分类规则":tab==3?"词根条目":"完整名字条目"));this.index=index;this.rule=rule;
            value="";
            if(index>=0) {
                if(rule) { var entry=draft.biomeRules.get(index);value=entry.biome();ruleKind=entry.kind(); }
                else { var entry=names().get(index);value=entry.text();kinds.addAll(entry.kinds());climate=entry.climate(); }
            } else kinds.add("ALL");
        }
        private ButtonWidget addButton(int bx,int by,int bw,String label,Runnable action) {
            return addDrawableChild(ButtonWidget.builder(Text.literal(label),b -> { value=text.getText();action.run();if(client.currentScreen==this) clearAndInit(); }).dimensions(bx,by,bw,20).build());
        }
        @Override protected void init() {
            if(text!=null) value=text.getText();
            w=Math.min(300,width-24);left=(width-w)/2;top=Math.max(8,(height-224)/2);
            text=new TextFieldWidget(textRenderer,left,top+38,w,18,Text.literal(rule?"群系 ID":"名称"));text.setMaxLength(rule?256:48);text.setText(value);addDrawableChild(text);setInitialFocus(text);
            if(rule) addButton(left,top+68,w,"分类："+label(ruleKind),() -> ruleKind=TerrainKind.values()[(ruleKind.ordinal()+1)%TerrainKind.values().length]);
            else {
                addButton(left,top+66,w,"气候："+switch(climate){case "COLD" -> "寒冷";case "HOT" -> "炎热";case "TEMPERATE" -> "温和";default -> "不限";},() -> {
                    List<String> options=List.of("ANY","COLD","TEMPERATE","HOT");climate=options.get((options.indexOf(climate)+1)%4);
                });
                addButton(left,top+92,w,(kinds.contains("ALL")?"✓ ":"")+"适用于所有地形",() -> { kinds.clear();kinds.add("ALL"); });
                int bw=(w-6)/4;
                for(int i=0;i<KINDS.length;i++) { String kind=KINDS[i].name();
                    addButton(left+(i%4)*(bw+2),top+118+(i/4)*24,bw,(kinds.contains(kind)?"✓ ":"")+LABELS[i],() -> {
                        kinds.remove("ALL");if(!kinds.remove(kind)) kinds.add(kind);
                    });
                }
            }
            addButton(left,top+194,(w-6)/2,"应用到草稿",this::apply);
            addButton(left+(w+6)/2,top+194,(w-6)/2,"取消",this::close);
        }
        private void apply() {
            try {
                if(rule) {
                    var entry=new AutoConfig.BiomeRule(text.getText().strip(),ruleKind);
                    AutoConfig check=draft.copy();if(index<0) check.biomeRules.add(entry);else check.biomeRules.set(index,entry);check.validate();
                    draft.biomeRules=check.biomeRules;
                } else {
                    if(text.getText().isBlank()) throw new IllegalArgumentException("请输入名称");
                    if(text.getText().codePoints().anyMatch(Character::isDigit)) throw new IllegalArgumentException("名称不能包含数字");
                    if(kinds.isEmpty()) throw new IllegalArgumentException("请至少选择一种适用地形");
                    var entry=new AutoConfig.Name(text.getText().strip(),List.copyOf(kinds),climate);
                    AutoConfig check=draft.copy();List<AutoConfig.Name> list=tab==3?check.roots:check.names;
                    if(index<0) list.add(entry);else list.set(index,entry);check.validate();
                    if(tab==3) draft.roots=list;else draft.names=list;
                }
                close();
            } catch(IllegalArgumentException ex) { entryError=ex.getMessage(); }
        }
        @Override public void close() { client.setScreen(AutomaticConfigScreen.this); }
        @Override public void render(DrawContext context,int mx,int my,float delta) {
            ClientUi.background(this,context,mx,my,delta);super.render(context,mx,my,delta);
            context.drawCenteredTextWithShadow(textRenderer,title,width/2,top,0xffffff);
            context.drawTextWithShadow(textRenderer,rule?"群系 ID（支持末尾 *）":"名称（不含数字）",left,top+23,0xbbbbbb);
            if(rule) context.drawTextWithShadow(textRenderer,"例：example:lavender_field",left,top+99,0xaaaaaa);
            context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(entryError.isEmpty()?"返回后点击“保存并应用”才会写入设置":entryError,w),width/2,top+174,entryError.isEmpty()?0xaaaaaa:0xff8888);
        }
    }
}
