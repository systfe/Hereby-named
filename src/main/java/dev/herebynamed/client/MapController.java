package dev.herebynamed.client;

import dev.herebynamed.domain.*;
import dev.herebynamed.mixin.GuiMapAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xaero.map.gui.GuiMap;
import java.util.*;
import java.util.function.IntConsumer;

final class MapController {
    private final GuiMap map;
    private final GuiMapAccessor view;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ClientRegions data = HerebyNamedClient.REGIONS;
    private final MapKnowledge knowledge;
    private final List<ClickableWidget> controls = new ArrayList<>();
    private final Map<Integer,String> remembered = new HashMap<>();
    private final TreeMap<Integer,TreeMap<Integer,RegionBook.Region>> overlay = new TreeMap<>();
    private String dimension = "", selected = "";
    private int layer = 1, visibility, displayLayer = 1;
    private long generation = -1;
    private SelectionJob job;
    private int pressed = -1, gesture;
    private double downX, downY;
    private ChunkCell start;
    private long downAt;
    private boolean triggered;
    private Popup popup;
    private RegionEdit.Action expected;
    private Set<String> previousRegionIds = Set.of();
    private Set<Integer> previousLayers = Set.of();
    private long expectedGeneration;

    MapController(GuiMap map) { this.map = map; this.view = (GuiMapAccessor)map; knowledge = new MapKnowledge(map.getMapProcessor()); }
    GuiMap screen() { return map; }
    void close() { job = null; pressed = -1; data.exit(); }
    private String dimension() {
        var current = map.getMapProcessor().getMapWorld().getCurrentDimension();
        return current == null ? "" : current.getDimId().getValue().toString();
    }
    private RegionBook book() { return data.book(dimension); }
    private boolean editing() { return data.editing(dimension); }
    void build() {
        Screens.getButtons(map).removeAll(controls); controls.clear(); popup = null;
        String next = dimension();
        if (!dimension.equals(next)) {
            if (!dimension.isEmpty()) data.exit();
            dimension = next; remembered.clear(); selected = ""; layer = displayLayer = 1; job = null; pressed = -1;
        }
        if (dimension.isEmpty()) return;
        RegionBook book = book();
        if (expected != null && data.generation != expectedGeneration) {
            if (expected == RegionEdit.Action.ADD_LAYER) book.state().layers().stream().filter(i -> !previousLayers.contains(i)).findFirst().ifPresent(i -> layer=i);
            if (expected == RegionEdit.Action.CREATE) book.regions().stream().filter(r -> !previousRegionIds.contains(r.id())).findFirst().ifPresent(r -> { selected=r.id(); layer=r.layer(); });
            expected=null;
        }
        if (!book.state().layers().contains(layer)) layer = book.state().layers().stream().min(Integer::compareTo).orElse(0);
        if (!book.state().layers().contains(displayLayer)) displayLayer = layer;
        if (book.region(selected).filter(r -> r.layer() == layer).isEmpty()) {
            selected = book.regions().stream().filter(r -> r.layer() == layer).reduce((a,b) -> b).map(RegionBook.Region::id).orElse("");
        }
        icon(0,40,visibilityIcon(),visibilityText(),visibility==1,b -> cycleVisibility());
        icon(0,60,0,editing() ? "退出区域编辑模式" : "切换区域编辑模式",editing(), b -> {
            if (editing()) { job = null; pressed = -1; data.exit(); } else data.enter(dimension);
            build();
        });
        if (editing()) {
            int[] cursor = {Math.max(24,(map.width-299)/2), 48};
            RegionBook.Region current = book.region(selected).orElse(null);
            select(cursor, current == null ? "新增选区…" : current.name(), 96,
                    "区域选择 · Tab 切换"+(current == null ? "" : " · "+current.name()), current == null ? 0x888888 : current.color(), b -> {
                List<RegionBook.Region> regions = currentRegions();
                openPopup(b, regions.stream().map(RegionBook.Region::name).toList(), i -> { selected = regions.get(i).id(); remembered.put(layer,selected); build(); });
            });
            tool(cursor,5,"新增选区",false,b -> client.setScreen(new RegionDialog(map,null,(name,color) -> edit(RegionEdit.Action.CREATE,name,color,Set.of()))));
            tool(cursor,6,"修改区域名称与颜色",false,b -> book.region(selected).ifPresent(r -> client.setScreen(new RegionDialog(map,r,(name,color) -> edit(RegionEdit.Action.RENAME,name,color,Set.of()))))).active=current!=null;
            tool(cursor,7,"删除选区",false,b -> book.region(selected).ifPresent(r -> client.setScreen(new DeleteDialog(map,"删除区域“"+r.name()+"”？",() -> edit(RegionEdit.Action.DELETE_REGION,"",0,Set.of()))))).active=current!=null;
            cursor[0]+=6;
            select(cursor,"层 "+layer,44,"图层选择 · 当前图层 "+layer,0xaaaaaa,b -> chooseLayer(b,false));
            tool(cursor,8,"新增图层",false,b -> edit(RegionEdit.Action.ADD_LAYER,"",0,Set.of()));
            tool(cursor,9,"删除当前图层及其区域",false,b -> {
                long count = book.regions().stream().filter(r -> r.layer() == layer).count();
                client.setScreen(new DeleteDialog(map,"删除图层 "+layer+" 及其 "+count+" 个区域？",() -> edit(RegionEdit.Action.DELETE_LAYER,"",0,Set.of())));
            });
            tool(cursor,visibilityIcon(),visibilityText(),visibility==1,b -> cycleVisibility());
            tool(cursor,10,"左键单击选择；左键拖动地图；右键移除；Shift 拖动矩形；Ctrl 原地长按选择连续群系；Tab 切换区域；Ctrl+Z 撤销；Ctrl+Y 重做；Esc 取消选择",false,b -> {});
        }
        generation = data.generation; rebuildOverlay();
    }
    private String visibilityText() { return switch (visibility) { case 1 -> "显示此图层 · "+(editing()?layer:displayLayer); case 2 -> "不显示所有"; default -> "显示所有图层"; }; }
    private void cycleVisibility() {
        visibility = (visibility+1)%3;
        if (visibility == 1 && !editing()) {
            List<Integer> layers = book().state().layers().stream().sorted().toList();
            popup = new Popup(22,40,layers.stream().map(i -> "图层 "+i).toList(),i -> { displayLayer = layers.get(i); build(); });
            rebuildOverlay();
        } else build();
    }
    private void chooseLayer(ButtonWidget button, boolean displayOnly) {
        List<Integer> layers = book().state().layers().stream().sorted().toList();
        openPopup(button,layers.stream().map(i -> "图层 "+i).toList(),i -> {
            if (displayOnly) displayLayer = layers.get(i);
            else { remembered.put(layer,selected); layer = layers.get(i); selected = remembered.getOrDefault(layer,""); }
            build();
        });
    }
    private void openPopup(ButtonWidget source, List<String> names, IntConsumer select) {
        if (names.isEmpty()) { data.message("此图层尚无区域，请先新建"); return; }
        popup = new Popup(source.getX(),source.getY()+21,names,select);
    }
    private int visibilityIcon() { return 2+visibility; }
    private void place(int[] cursor,int width) {
        if(cursor[0]+width>map.width-24 && cursor[0]>24) { cursor[0]=24; cursor[1]+=22; }
    }
    private ButtonWidget tool(int[] cursor,int icon,String hint,boolean selected,ButtonWidget.PressAction action) {
        place(cursor,20);
        ButtonWidget button=icon(cursor[0],cursor[1],icon,hint,selected,action); cursor[0]+=21; return button;
    }
    private void select(int[] cursor,String value,int width,String hint,int color,ButtonWidget.PressAction action) {
        place(cursor,width);
        add(MapTools.selector(cursor[0],cursor[1],width,value,hint,color,() -> popup==null,action)); cursor[0]+=width+3;
    }
    private ButtonWidget icon(int x,int y,int icon,String hint,boolean selected,ButtonWidget.PressAction action) {
        ButtonWidget button=MapTools.icon(x,y,icon,hint,selected,() -> popup==null,action); add(button); return button;
    }
    private void add(ButtonWidget button) { controls.add(button); Screens.getButtons(map).add(button); }
    private List<RegionBook.Region> currentRegions() { return book().regions().stream().filter(r -> r.layer()==layer).toList(); }
    private void edit(RegionEdit.Action action, String name, int color, Set<ChunkCell> cells) {
        if (job != null || data.busy()) return;
        expected=action; expectedGeneration=data.generation;
        previousLayers=book().state().layers();
        previousRegionIds=new HashSet<>(); for(var r:book().regions()) previousRegionIds.add(r.id());
        data.edit(dimension,action,layer,selected,name,color,cells);
    }
    private ChunkCell cellAt(double x, double y) {
        double guiScale = client.getWindow().getScaleFactor(), scale = Math.max(0.000001,view.hereby$scale());
        int bx = (int)Math.floor(view.hereby$cameraX() + (x*guiScale-client.getWindow().getFramebufferWidth()/2.0)/scale);
        int bz = (int)Math.floor(view.hereby$cameraZ() + (y*guiScale-client.getWindow().getFramebufferHeight()/2.0)/scale);
        return new ChunkCell(bx>>4,bz>>4);
    }
    private boolean onControl(double x,double y) {
        return Screens.getButtons(map).stream().anyMatch(w -> w.visible && w.isMouseOver(x,y));
    }
    boolean mouseDown(double x,double y,int button) {
        if (popup != null) { popup.click(x,y,button); return false; }
        if (!editing() || onControl(x,y) || button>1) return true;
        if (job != null || data.busy()) return false;
        if (selected.isEmpty()) {
            if (button == 0 && !Screen.hasShiftDown() && !Screen.hasControlDown()) return true;
            data.message("请先新增选区"); return false;
        }
        pressed=button; downX=x; downY=y; downAt=System.nanoTime(); start=cellAt(x,y); triggered=false;
        gesture = Screen.hasControlDown()?2:Screen.hasShiftDown()?1:0;
        return gesture==0 && button==0;
    }
    boolean mouseUp(double x,double y,int button) {
        if (pressed != button) return true;
        int previousGesture=gesture; pressed=-1;
        if (editing() && !triggered) {
            if (previousGesture==1) begin(start,cellAt(x,y),false,button==0);
            else if (previousGesture==0 && Math.hypot(x-downX,y-downY)<=4) begin(start,start,false,button==0);
        }
        return previousGesture==0 && button==0;
    }
    boolean key(int key,int modifiers) {
        if (popup!=null && key==GLFW.GLFW_KEY_ESCAPE) { popup=null; return false; }
        if (!editing()) return true;
        if (key==GLFW.GLFW_KEY_ESCAPE && (job!=null || pressed>=0)) { job=null; pressed=-1; return false; }
        if (job!=null || data.busy()) return key==GLFW.GLFW_KEY_ESCAPE;
        if (key==GLFW.GLFW_KEY_TAB) {
            List<RegionBook.Region> regions=currentRegions();
            if (!regions.isEmpty()) {
                int index=0; for(int i=0;i<regions.size();i++) if(regions.get(i).id().equals(selected)) index=i;
                selected=regions.get(Math.floorMod(index+(Screen.hasShiftDown()?-1:1),regions.size())).id();
                remembered.put(layer,selected); build();
            }
            return false;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL)!=0 && (key==GLFW.GLFW_KEY_Z || key==GLFW.GLFW_KEY_Y)) {
            edit(key==GLFW.GLFW_KEY_Y || (modifiers & GLFW.GLFW_MOD_SHIFT)!=0 ? RegionEdit.Action.REDO:RegionEdit.Action.UNDO,"",0,Set.of()); return false;
        }
        return true;
    }
    private void begin(ChunkCell from,ChunkCell to,boolean flood,boolean add) {
        try { job=new SelectionJob(selected,add,knowledge,from,to,flood); }
        catch (IllegalArgumentException ex) { data.message(ex.getMessage()); }
    }
    void tick() {
        if (!dimension.equals(dimension()) || generation!=data.generation) build();
        if (!editing()) { job=null; pressed=-1; return; }
        if (pressed>=0 && gesture==2 && !triggered && System.nanoTime()-downAt>=500_000_000L) {
            double x=client.mouse.getX()/client.getWindow().getScaleFactor(), y=client.mouse.getY()/client.getWindow().getScaleFactor();
            if (Math.hypot(x-downX,y-downY)<=4) { triggered=true; begin(start,start,true,pressed==0); }
            else { downAt=System.nanoTime(); downX=x; downY=y; start=cellAt(x,y); }
        }
        if (job!=null) try {
            if (job.advance()) {
                SelectionJob completed=job; job=null;
                data.edit(dimension,completed.add?RegionEdit.Action.ADD:RegionEdit.Action.REMOVE,layer,completed.region,"",0,completed.result());
            }
        } catch (IllegalArgumentException ex) { job=null; data.message(ex.getMessage()); }
    }
    private void rebuildOverlay() {
        overlay.clear(); if (visibility==2 || dimension.isEmpty()) return;
        Integer shown=visibility==1?(editing()?layer:displayLayer):null;
        book().regions().stream().filter(r -> shown==null || r.layer()==shown).sorted(Comparator.comparingInt(RegionBook.Region::layer)).forEach(r -> {
            for (ChunkCell c:r.cells()) overlay.computeIfAbsent(c.x(),x->new TreeMap<>()).put(c.z(),r);
        });
    }
    void render(DrawContext context,int mouseX,int mouseY) {
        if (dimension.isEmpty() || GuiMap.hiddenUI) return;
        drawOverlay(context);
        context.getMatrices().push(); context.getMatrices().translate(0,0,500);
        List<String> names=book().namesAt(new ChunkCell(view.hereby$mouseX()>>4,view.hereby$mouseZ()>>4));
        for(int i=0;i<names.size();i++) {
            String name=client.textRenderer.trimToWidth(names.get(i),Math.max(30,map.width-160));
            int width=client.textRenderer.getWidth(name), y=24+i*11;
            context.fill(map.width/2-width/2-3,y-1,map.width/2+width/2+3,y+10,0x99000000);
            context.drawCenteredTextWithShadow(client.textRenderer,name,map.width/2,y,0xffffff);
        }
        for(ClickableWidget control:controls) control.render(context,mouseX,mouseY,0);
        if (editing() && (job!=null || data.busy())) {
            String status=job!=null?"正在选择 "+job.size()+" 个区块 · Esc 取消":"正在同步…";
            context.drawCenteredTextWithShadow(client.textRenderer,status,map.width/2,map.height-23,0xffffff);
        }
        if(popup!=null) popup.render(context,mouseX,mouseY);
        context.getMatrices().pop();
    }
    private void drawOverlay(DrawContext context) {
        if (overlay.isEmpty() || view.hereby$scale()<=0) return;
        double scale=view.hereby$scale()/client.getWindow().getScaleFactor();
        ChunkCell min=cellAt(0,0), max=cellAt(map.width,map.height);
        // Clip to the map viewport, never to the space between UI labels/toolbars.
        context.enableScissor(0,0,map.width,map.height);
        for(var column:overlay.subMap(min.x(),true,max.x(),true).entrySet()) {
            for(var entry:column.getValue().subMap(min.z(),true,max.z(),true).entrySet()) {
                ChunkCell c=new ChunkCell(column.getKey(),entry.getKey());
                if(knowledge.sample(c,false).biome()==null) continue;
                int x=(int)Math.floor(map.width/2.0+(c.x()*16.0-view.hereby$cameraX())*scale);
                int y=(int)Math.floor(map.height/2.0+(c.z()*16.0-view.hereby$cameraZ())*scale);
                int x2=(int)Math.ceil(map.width/2.0+((c.x()+1)*16.0-view.hereby$cameraX())*scale);
                int y2=(int)Math.ceil(map.height/2.0+((c.z()+1)*16.0-view.hereby$cameraZ())*scale);
                context.fill(x,y,x2,y2,0x55000000|entry.getValue().color());
            }
        }
        context.disableScissor();
    }
    private final class Popup {
        final int x,y,rows,width;
        final List<String> names;
        final IntConsumer selected;
        int offset;
        Popup(int x,int y,List<String> names,IntConsumer selected) {
            width=Math.min(Math.max(96,names.stream().mapToInt(client.textRenderer::getWidth).max().orElse(60)+16),Math.min(160,map.width-28));
            this.x=Math.min(x,Math.max(0,map.width-width-4)); this.y=y; this.names=names; this.selected=selected;
            rows=Math.max(1,Math.min(names.size(),Math.min(9,(map.height-y-34)/16)));
        }
        void click(double mx,double my,int button) {
            Popup current=this;
            if(button==0 && mx>=x && mx<x+width && my>=y && my<y+rows*16) {
                int index=offset+(int)(my-y)/16;
                if(index<names.size()) { popup=null; selected.accept(index); return; }
            }
            if(button==0 && mx>=x && mx<x+width && my>=y+rows*16 && my<y+rows*16+16 && names.size()>rows) {
                offset=Math.floorMod(offset+rows,names.size()); return;
            }
            if(popup==current) popup=null;
        }
        void render(DrawContext context,int mx,int my) {
            context.fill(x-1,y-1,x+width+1,y+rows*16+(names.size()>rows?17:1),0xcc000000);
            for(int i=0;i<rows && i+offset<names.size();i++) {
                boolean hover=mx>=x&&mx<x+width&&my>=y+i*16&&my<y+(i+1)*16;
                context.fill(x,y+i*16,x+width,y+(i+1)*16,hover?0x66555555:0x66000000);
                context.drawTextWithShadow(client.textRenderer,client.textRenderer.trimToWidth(names.get(i+offset),width-8),x+4,y+i*16+4,0xffffff);
            }
            if(names.size()>rows) context.drawTextWithShadow(client.textRenderer,"下一页 ▾",x+4,y+rows*16+4,0xffffff);
        }
    }
}
