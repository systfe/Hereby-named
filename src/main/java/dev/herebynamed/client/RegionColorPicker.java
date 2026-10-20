package dev.herebynamed.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.util.function.IntConsumer;

/** Saturation/brightness, hue, and ready-to-use swatches; no color code required. */
final class RegionColorPicker extends ClickableWidget {
    private static final int[] SWATCHES = {0xe85d5d,0xf4a340,0xf1d54c,0x82c95b,0x3fb88c,
            0x66bbdd,0x5b83dd,0x9a71d6,0xdc80b5,0xe8e8e8,0x666666};
    private float hue, saturation, brightness;
    private int rgb;
    private final IntConsumer changed;
    private int dragging = -1;

    RegionColorPicker(int x,int y,int width,int color,IntConsumer changed) {
        super(x,y,width,100,Text.literal("标记颜色：方向键调整饱和度和明暗，Shift 加方向键调整色相"));
        this.changed=changed;
        setColor(color);
    }
    private int squareWidth() { return width-24; }
    int color() { return rgb; }
    private void setColor(int rgb) {
        this.rgb=rgb&0xffffff;
        float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,b=(rgb&255)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),range=max-min;
        brightness=max; saturation=max==0?0:range/max;
        if(range!=0) {
            float component=max==r?(g-b)/range:max==g?(b-r)/range+2:(r-g)/range+4;
            hue=(component/6+1)%1;
        }
    }
    @Override protected void renderButton(DrawContext context,int mouseX,int mouseY,float delta) {
        int x=getX(),y=getY(),sw=squareWidth();
        for(int col=0;col<sw;col++) {
            int top=0xff000000|MathHelper.hsvToRgb(hue,col/(float)(sw-1),1);
            context.fillGradient(x+col,y,x+col+1,y+64,top,0xff000000);
        }
        for(int row=0;row<64;row++) {
            int color=0xff000000|MathHelper.hsvToRgb(row/64f,1,1);
            context.fill(x+width-16,y+row,x+width,y+row+1,color);
        }
        int markerX=x+Math.round(saturation*(sw-1)),markerY=y+Math.round((1-brightness)*63);
        border(context,markerX-3,markerY-3,7,7,0xff000000);
        border(context,markerX-2,markerY-2,5,5,0xffffffff);
        int hueY=y+Math.round(hue*63);
        border(context,x+width-17,hueY-2,18,5,0xff000000);
        border(context,x+width-16,hueY-1,16,3,0xffffffff);
        for(int i=0;i<SWATCHES.length;i++) {
            int left=x+i*20;
            context.fill(left,y+76,left+17,y+93,0xff000000|SWATCHES[i]);
            if(color()==SWATCHES[i] || (mouseX>=left&&mouseX<left+17&&mouseY>=y+76&&mouseY<y+93))
                border(context,left-1,y+75,19,19,0xffffffff);
        }
        if(isFocused()) border(context,x-2,y-2,width+4,68,0xffaaaaaa);
    }
    private static void border(DrawContext context,int x,int y,int width,int height,int color) {
        context.fill(x,y,x+width,y+1,color); context.fill(x,y+height-1,x+width,y+height,color);
        context.fill(x,y,x+1,y+height,color); context.fill(x+width-1,y,x+width,y+height,color);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if(button!=0||!active||!visible||!isMouseOver(mouseX,mouseY)) return false;
        double x=mouseX-getX(),y=mouseY-getY();
        if(y>=76&&y<93) {
            int swatch=(int)x/20;
            if(swatch<SWATCHES.length&&x%20<17) { setColor(SWATCHES[swatch]); changed.accept(color()); playDownSound(net.minecraft.client.MinecraftClient.getInstance().getSoundManager()); }
            return true;
        }
        dragging=y<64?(x<squareWidth()?0:x>=width-16?1:-1):-1;
        update(mouseX,mouseY); return true;
    }
    private void update(double mouseX,double mouseY) {
        if(dragging==0) {
            saturation=MathHelper.clamp((float)(mouseX-getX())/(squareWidth()-1),0,1);
            brightness=1-MathHelper.clamp((float)(mouseY-getY())/63,0,1);
        } else if(dragging==1) hue=MathHelper.clamp((float)(mouseY-getY())/64,0,0.99999f);
        else return;
        rgb=MathHelper.hsvToRgb(hue,saturation,brightness)&0xffffff;
        changed.accept(color());
    }
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double deltaX,double deltaY) {
        if(button!=0||dragging<0) return false;
        update(mouseX,mouseY); return true;
    }
    @Override public boolean mouseReleased(double mouseX,double mouseY,int button) {
        if(button!=0||dragging<0) return false;
        dragging=-1; return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(!isFocused()||key<GLFW.GLFW_KEY_RIGHT||key>GLFW.GLFW_KEY_UP) return false;
        float step=(key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_UP)?0.02f:-0.02f;
        if(Screen.hasShiftDown()) hue=(hue+step+1)%1;
        else if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT) saturation=MathHelper.clamp(saturation+step,0,1);
        else brightness=MathHelper.clamp(brightness+step,0,1);
        rgb=MathHelper.hsvToRgb(hue,saturation,brightness)&0xffffff;
        changed.accept(color()); return true;
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
