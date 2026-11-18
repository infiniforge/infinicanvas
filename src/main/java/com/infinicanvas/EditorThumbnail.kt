package com.infinicanvas

import android.content.Context
import android.graphics.*
import android.view.View

/** Untinted document pixels on a transparency checker, independent of the button theme. */
class EditorThumbnail(context:Context,private val image:Bitmap?=null,swatch:Int?=null,private val index:Int?=null):View(context) {
    init { setLayerType(View.LAYER_TYPE_SOFTWARE,null) }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var swatchColor:Int?=swatch
        set(value) { field=value;invalidate() }
    private val d=resources.displayMetrics.density
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val bottom=height.toFloat()
        val rect=RectF(.5f*d,.5f*d,width-.5f*d,bottom-.5f*d)
        canvas.save();canvas.clipRect(rect)
        val cell=8*d
        for(y in 0..(bottom/cell).toInt()) for(x in 0..(width/cell).toInt()) {
            paint.color=context.getColor(if((x+y)%2==0) R.color.checker_a else R.color.checker_b)
            canvas.drawRect(x*cell,y*cell,(x+1)*cell,(y+1)*cell,paint)
        }
        swatchColor?.let { paint.color=it;canvas.drawRect(rect,paint) }
        if(image!=null) {
            val scale=minOf(rect.width()/image.width,rect.height()/image.height)
            val w=image.width*scale;val h=image.height*scale
            paint.color=Color.WHITE
            canvas.drawBitmap(image,null,RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2),paint)
        }
        canvas.restore()
        paint.color=context.getColor(R.color.outline);paint.style=Paint.Style.STROKE;paint.strokeWidth=d
        canvas.drawRect(rect,paint);paint.style=Paint.Style.FILL
        if(index!=null) {
            val argb=swatchColor!!
            val alpha=(argb ushr 24)/255f;val checker=context.getColor(R.color.checker_a)
            fun channel(shift:Int)=((argb ushr shift) and 255)*alpha+((checker ushr shift) and 255)*(1-alpha)
            val luminance=channel(16)*.299f+channel(8)*.587f+channel(0)*.114f
            paint.color=if(luminance>145) Color.BLACK else Color.WHITE
            paint.textAlign=Paint.Align.CENTER;paint.textSize=12*resources.displayMetrics.scaledDensity
            val text=index.toString()
            if(paint.measureText(text)>width-3*d) paint.textSize*=((width-3*d)/paint.measureText(text)).coerceAtLeast(.2f)
            val metrics=paint.fontMetrics
            canvas.drawText(text,width/2f,height/2f-(metrics.ascent+metrics.descent)/2,paint)
        }
        if(isSelected) {
            paint.style=Paint.Style.STROKE;paint.strokeWidth=3*d;paint.color=Color.BLACK;canvas.drawRect(rect,paint)
            paint.strokeWidth=1.5f*d;paint.color=context.getColor(R.color.accent);canvas.drawRect(rect,paint);paint.style=Paint.Style.FILL
        }
        if(isPressed) { paint.color=0x33777777;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint) }
    }
    override fun drawableStateChanged() { super.drawableStateChanged();invalidate() }
    override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if(isClickable) info.className="android.widget.Button"
    }
    override fun performClick():Boolean { super.performClick();return true }
}
