package com.infinicanvas

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Saturation/value field and hue strip; exact text input remains an accessible alternative. */
class HsvColorPicker(context:Context,initial:Int):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv=FloatArray(3).also { Color.colorToHSV(initial,it) }
    private val field=RectF()
    private val hue=RectF()
    private var channel=0
    private val d=resources.displayMetrics.density
    var onColorChanged:((Int)->Unit)?=null
    var color:Int
        get()=Color.HSVToColor(hsv)
        set(value) { Color.colorToHSV(value,hsv);invalidate() }
    init { isClickable=true;contentDescription="Color picker. Drag in the color field or hue strip; use the hex field for an exact color." }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        field.set(10*d,8*d,w-10*d,h-58*d);hue.set(10*d,h-40*d,w-10*d,h-16*d)
    }
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        val hueColor=Color.HSVToColor(floatArrayOf(hsv[0],1f,1f))
        p.shader=LinearGradient(field.left,0f,field.right,0f,Color.WHITE,hueColor,Shader.TileMode.CLAMP);c.drawRect(field,p)
        p.shader=LinearGradient(0f,field.top,0f,field.bottom,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP);c.drawRect(field,p)
        p.shader=LinearGradient(hue.left,0f,hue.right,0f,intArrayOf(Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED),null,Shader.TileMode.CLAMP);c.drawRoundRect(hue,4*d,4*d,p)
        p.shader=null
        fun marker(x:Float,y:Float,r:Float) { p.style=Paint.Style.STROKE;p.strokeWidth=3*d;p.color=Color.BLACK;c.drawCircle(x,y,r,p);p.strokeWidth=1.5f*d;p.color=Color.WHITE;c.drawCircle(x,y,r,p);p.style=Paint.Style.FILL }
        marker(field.left+hsv[1]*field.width(),field.top+(1-hsv[2])*field.height(),6*d)
        marker(hue.left+hsv[0]/360f*hue.width(),hue.centerY(),9*d)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!isEnabled) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                channel=if(event.y>=hue.top-8*d) 1 else 0
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false);performClick() }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false);return true }
        }
        if(channel==1) hsv[0]=((event.x-hue.left)/hue.width()).coerceIn(0f,.99999f)*360f
        else { hsv[1]=((event.x-field.left)/field.width()).coerceIn(0f,1f);hsv[2]=1f-((event.y-field.top)/field.height()).coerceIn(0f,1f) }
        invalidate();onColorChanged?.invoke(color);return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}
