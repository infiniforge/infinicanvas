package com.infinicanvas

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/** UI-only viewport and normalized crop mapping. Processing is delegated by the host. */
class CanvasPreview(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageRect = RectF()
    private var bitmap: Bitmap? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var drag = -1
    private var adjustmentFilter: ColorMatrixColorFilter? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var tap = false
    var layerBounds:RectF?=null
        set(value) { field=value;invalidate() }
    var onLayerMove:((Int,Int)->Unit)?=null
    var onLayerPick:((Int,Int)->Unit)?=null
    var editingEnabled=true
        set(value) { field=value;if(!value) { selecting=false;stroke.clear();movingLayer=false;drag=-1;layerDx=0f;layerDy=0f };invalidate() }
    var selectionTool:String?=null
    var selectionMask:Bitmap?=null
        set(value) { field=value;invalidate() }
    var selectionBounds:Rect?=null
        set(value) { field=value;invalidate() }
    var onSelectionShape:((String,List<PointF>)->Unit)?=null
    var onSelectionPoint:((Int,Int)->Unit)?=null
    private var selecting=false
    private val stroke=mutableListOf<PointF>()
    private var focusX=0f
    private var focusY=0f
    private var movingLayer=false
    private var layerDx=0f
    private var layerDy=0f
    private var drawable:android.graphics.drawable.Drawable?=null
    fun animation(value:android.graphics.drawable.Drawable?,running:Boolean) {
        if(drawable!==value) {
            (drawable as? android.graphics.drawable.Animatable)?.stop();drawable?.callback=null
            drawable=value;value?.callback=this
        }
        (drawable as? android.graphics.drawable.Animatable)?.let { if(running && !it.isRunning) it.start() else if(!running && it.isRunning) it.stop() }
        invalidate()
    }
    override fun verifyDrawable(who:android.graphics.drawable.Drawable)=who===drawable || super.verifyDrawable(who)
    override fun onDetachedFromWindow() { (drawable as? android.graphics.drawable.Animatable)?.stop();super.onDetachedFromWindow() }
    private fun selectedRect():RectF? {
        val image=bitmap ?: return null
        return layerBounds?.let { r -> RectF(imageRect.left+(r.left+layerDx)*imageRect.width()/image.width,
            imageRect.top+(r.top+layerDy)*imageRect.height()/image.height,
            imageRect.left+(r.right+layerDx)*imageRect.width()/image.width,
            imageRect.top+(r.bottom+layerDy)*imageRect.height()/image.height) }
    }
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    var crop = Edit.Crop(.1f, .1f, .9f, .9f)
        set(value) { field = value; invalidate() }
    var cropping = false
        set(value) { field = value; if (value) fit(); invalidate() }
    var adjustment = Edit.Adjust()
        set(value) {
            field = value
            adjustmentFilter = if (value.isIdentity) null else ColorMatrixColorFilter(ImageRenderer.colors(value))
            invalidate()
        }
    var onCropChanged: (() -> Unit)? = null
    var onViewportChanged: ((Int) -> Unit)? = null

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        isClickable = true
        isFocusable = true
        contentDescription = "Image canvas. Pinch to zoom, drag to pan, double tap to fit."
        setBackgroundColor(context.getColor(R.color.workspace))
    }

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = zoom
            val candidate=zoom*detector.scaleFactor
            val extent=maxOf(bounds().width(),bounds().height())/zoom*candidate
            if(!candidate.isFinite() || candidate<=0f || !extent.isFinite() || extent<1f) return true
            zoom=candidate
            val ratio = zoom / old
            panX = (panX - (detector.focusX - width / 2f)) * ratio + (detector.focusX - width / 2f)
            panY = (panY - (detector.focusY - height / 2f)) * ratio + (detector.focusY - height / 2f)
            clampPan(); invalidate(); onViewportChanged?.invoke((zoom * 100).roundToInt())
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onDoubleTap(event: MotionEvent): Boolean { if (!cropping && selectionTool==null) fit(); return true }
    })

    fun display(value: Bitmap?, resetViewport: Boolean = true) {
        if (bitmap !== value) { bitmap = value; if (resetViewport) fit() }
        invalidate()
    }
    fun fit() { zoom = 1f; panX = 0f; panY = 0f; invalidate(); onViewportChanged?.invoke(100) }
    fun actualPixels() {
        val image=bitmap ?: return
        val fitted=min((width-16*density).coerceAtLeast(1f)/image.width,(height-16*density).coerceAtLeast(1f)/image.height)
        zoom=1f/fitted;panX=0f;panY=0f;invalidate()
    }
    fun cropPixelBounds():Rect {
        val image=bitmap ?: return Rect(0,0,1,1)
        val x=(crop.left*image.width).roundToInt().coerceIn(0,image.width-1)
        val y=(crop.top*image.height).roundToInt().coerceIn(0,image.height-1)
        return Rect(x,y,(crop.right*image.width).roundToInt().coerceIn(x+1,image.width),(crop.bottom*image.height).roundToInt().coerceIn(y+1,image.height))
    }
    fun preset(ratio: Float?) {
        val image = bitmap ?: return
        if (ratio == null) crop = Edit.Crop(0f, 0f, 1f, 1f)
        else {
            val aspect = image.width.toFloat() / image.height
            val w = min(1f, ratio / aspect)
            val h = min(1f, aspect / ratio)
            crop = Edit.Crop((1f-w)/2, (1f-h)/2, (1f+w)/2, (1f+h)/2)
        }
        onCropChanged?.invoke()
    }
    private fun bounds(): RectF {
        val image = bitmap ?: return RectF()
        val fit = min((width - 16*density).coerceAtLeast(1f) / image.width,
            (height - 16*density).coerceAtLeast(1f) / image.height)
        val w = image.width * fit * zoom; val h = image.height * fit * zoom
        return RectF((width-w)/2+panX, (height-h)/2+panY, (width+w)/2+panX, (height+h)/2+panY)
    }
    private fun clampPan() {
        val rect = bounds()
        val x = max(0f, (rect.width()-width)/2 + 32*density)
        val y = max(0f, (rect.height()-height)/2 + 32*density)
        panX = panX.coerceIn(-x, x); panY = panY.coerceIn(-y, y)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w,h,oldw,oldh); clampPan() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap
        if (image == null) return
        imageRect.set(bounds())
        canvas.save(); canvas.clipRect(imageRect)
        // Anchor the transparency pattern to the viewport; it never changes source pixels.
        val cell = 12*density
        val left = max(0f, imageRect.left); val top = max(0f, imageRect.top)
        val right = min(width.toFloat(), imageRect.right); val bottom = min(height.toFloat(), imageRect.bottom)
        for (y in (top/cell).toInt()..(bottom/cell).toInt()) for (x in (left/cell).toInt()..(right/cell).toInt()) {
            paint.color = context.getColor(if ((x+y)%2==0) R.color.checker_a else R.color.checker_b)
            canvas.drawRect(x*cell,y*cell,(x+1)*cell,(y+1)*cell,paint)
        }
        paint.color = Color.WHITE
        paint.isFilterBitmap=imageRect.width()/image.width<2*density
        paint.colorFilter = adjustmentFilter
        if(drawable!=null) { drawable!!.bounds=Rect(imageRect.left.roundToInt(),imageRect.top.roundToInt(),imageRect.right.roundToInt(),imageRect.bottom.roundToInt());drawable!!.draw(canvas) }
        else canvas.drawBitmap(image, null, imageRect, paint)
        paint.colorFilter = null
        val pixel=imageRect.width()/image.width
        if(pixel>=8*density) {
            paint.color=context.getColor(R.color.outline);paint.alpha=100;paint.strokeWidth=1f
            val firstX=maxOf(1,((-imageRect.left)/pixel).toInt());val lastX=minOf(image.width,((width-imageRect.left)/pixel).toInt()+1)
            val firstY=maxOf(1,((-imageRect.top)/pixel).toInt());val lastY=minOf(image.height,((height-imageRect.top)/pixel).toInt()+1)
            for(x in firstX..lastX) canvas.drawLine(imageRect.left+x*pixel,imageRect.top,imageRect.left+x*pixel,imageRect.bottom,paint)
            for(y in firstY..lastY) canvas.drawLine(imageRect.left,imageRect.top+y*pixel,imageRect.right,imageRect.top+y*pixel,paint)
            paint.alpha=255
        }
        if(selectionTool!=null) drawSelection(canvas,image)
        if (cropping) drawCrop(canvas)
        selectedRect()?.let { r ->
            paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.color=Color.BLACK;canvas.drawRect(r,paint)
            paint.strokeWidth=density;paint.color=context.getColor(R.color.accent);canvas.drawRect(r,paint);paint.style=Paint.Style.FILL
            for((x,y) in listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom)) canvas.drawCircle(x,y,4*density,paint)
        }
        canvas.restore()
    }

    private fun imagePoint(x:Float,y:Float):PointF {
        val image=bitmap!!
        return PointF(((x-imageRect.left)*image.width/imageRect.width()).coerceIn(0f,image.width.toFloat()),
            ((y-imageRect.top)*image.height/imageRect.height()).coerceIn(0f,image.height.toFloat()))
    }
    private fun drawSelection(canvas:Canvas,image:Bitmap) {
        selectionMask?.takeIf { it.width==image.width && it.height==image.height }?.let {
            paint.color=context.getColor(R.color.accent);paint.alpha=96;paint.isFilterBitmap=false
            canvas.drawBitmap(it,null,imageRect,paint);paint.alpha=255
        }
        fun point(p:PointF)=PointF(imageRect.left+p.x*imageRect.width()/image.width,imageRect.top+p.y*imageRect.height()/image.height)
        val path=Path()
        if(selecting && stroke.isNotEmpty()) {
            val a=point(stroke.first());val b=point(stroke.last())
            if(selectionTool=="lasso") { path.moveTo(a.x,a.y);stroke.drop(1).forEach { val p=point(it);path.lineTo(p.x,p.y) };path.close() }
            else if(selectionTool in setOf("rect","ellipse")) {
                val r=RectF(min(a.x,b.x),min(a.y,b.y),max(a.x,b.x),max(a.y,b.y))
                if(selectionTool=="ellipse") path.addOval(r,Path.Direction.CW) else path.addRect(r,Path.Direction.CW)
            }
        } else selectionBounds?.let { r -> val a=point(PointF(r.left.toFloat(),r.top.toFloat()));val b=point(PointF(r.right.toFloat(),r.bottom.toFloat()));path.addRect(a.x,a.y,b.x,b.y,Path.Direction.CW) }
        paint.style=Paint.Style.STROKE;paint.color=Color.BLACK;paint.strokeWidth=3*density;canvas.drawPath(path,paint)
        paint.color=Color.WHITE;paint.strokeWidth=density;paint.pathEffect=DashPathEffect(floatArrayOf(4*density,4*density),0f);canvas.drawPath(path,paint)
        paint.pathEffect=null;paint.style=Paint.Style.FILL
    }

    private fun cropRect() = RectF(imageRect.left+crop.left*imageRect.width(), imageRect.top+crop.top*imageRect.height(),
        imageRect.left+crop.right*imageRect.width(), imageRect.top+crop.bottom*imageRect.height())
    private fun drawCrop(canvas: Canvas) {
        val r = cropRect()
        paint.color = 0x99000000.toInt()
        canvas.drawRect(imageRect.left,imageRect.top,imageRect.right,r.top,paint)
        canvas.drawRect(imageRect.left,r.bottom,imageRect.right,imageRect.bottom,paint)
        canvas.drawRect(imageRect.left,r.top,r.left,r.bottom,paint)
        canvas.drawRect(r.right,r.top,imageRect.right,r.bottom,paint)
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
        canvas.drawRect(r,paint)
        paint.alpha = 100
        for (i in 1..2) {
            canvas.drawLine(r.left+r.width()*i/3,r.top,r.left+r.width()*i/3,r.bottom,paint)
            canvas.drawLine(r.left,r.top+r.height()*i/3,r.right,r.top+r.height()*i/3,paint)
        }
        paint.alpha = 255; paint.style = Paint.Style.FILL
        for ((x,y) in listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom)) {
            canvas.drawCircle(x,y,6*density,paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || !isEnabled) return false
        gestures.onTouchEvent(event)
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastX = event.x; lastY = event.y
                touchStartX = event.x; touchStartY = event.y; tap = true
                imageRect.set(bounds());layerDx=0f;layerDy=0f
                movingLayer=editingEnabled && !cropping && selectionTool==null && selectedRect()?.contains(event.x,event.y)==true
                selecting=editingEnabled && selectionTool!=null && imageRect.contains(event.x,event.y)
                stroke.clear();if(selecting) stroke+=imagePoint(event.x,event.y)
                if (cropping && editingEnabled) {
                    imageRect.set(bounds())
                    val r = cropRect()
                    val corners = listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom)
                    drag = corners.indices.minByOrNull { hypot(event.x-corners[it].first,event.y-corners[it].second) } ?: -1
                    if (drag >= 0 && hypot(event.x-corners[drag].first,event.y-corners[drag].second) > 32*density)
                        drag = if (r.contains(event.x,event.y)) 4 else -1
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                tap=false;movingLayer=false;selecting=false;stroke.clear();drag=-1;layerDx=0f;layerDy=0f
                focusX=(event.getX(0)+event.getX(1))/2;focusY=(event.getY(0)+event.getY(1))/2;invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x-touchStartX,event.y-touchStartY) > touchSlop) tap = false
                if(event.pointerCount>=2) {
                    val x=(event.getX(0)+event.getX(1))/2;val y=(event.getY(0)+event.getY(1))/2
                    panX+=x-focusX;panY+=y-focusY;focusX=x;focusY=y;clampPan();invalidate()
                } else if(selecting) {
                    val next=imagePoint(event.x,event.y)
                    if(selectionTool=="lasso") {
                        if(stroke.size>=4096) { val sampled=stroke.filterIndexed { i,_ -> i%2==0 };stroke.clear();stroke.addAll(sampled) }
                        if(hypot(next.x-stroke.last().x,next.y-stroke.last().y)>=.25f) stroke+=next
                    } else if(stroke.size==1) stroke+=next else stroke[stroke.lastIndex]=next
                    invalidate()
                } else if (editingEnabled && cropping && drag >= 0 && !scale.isInProgress) {
                    val dx = (event.x-lastX)/imageRect.width(); val dy = (event.y-lastY)/imageRect.height()
                    val c = crop
                    val minX = min(c.right-c.left, 1f / bitmap!!.width); val minY = min(c.bottom-c.top, 1f / bitmap!!.height)
                    crop = if (drag == 4) {
                        val x = dx.coerceIn(-c.left,1f-c.right); val y = dy.coerceIn(-c.top,1f-c.bottom)
                        Edit.Crop(c.left+x,c.top+y,c.right+x,c.bottom+y)
                    } else Edit.Crop(
                        if (drag==0||drag==2) (c.left+dx).coerceIn(0f,c.right-minX) else c.left,
                        if (drag==0||drag==1) (c.top+dy).coerceIn(0f,c.bottom-minY) else c.top,
                        if (drag==1||drag==3) (c.right+dx).coerceIn(c.left+minX,1f) else c.right,
                        if (drag==2||drag==3) (c.bottom+dy).coerceIn(c.top+minY,1f) else c.bottom)
                    onCropChanged?.invoke()
                } else if(movingLayer && event.pointerCount==1) {
                    layerDx=(event.x-touchStartX)*bitmap!!.width/imageRect.width()
                    layerDy=(event.y-touchStartY)*bitmap!!.height/imageRect.height();invalidate()
                } else if ((!cropping || !editingEnabled) && !scale.isInProgress && event.pointerCount == 1) {
                    panX += event.x-lastX; panY += event.y-lastY; clampPan(); invalidate()
                }
                lastX=event.x; lastY=event.y
            }
            MotionEvent.ACTION_POINTER_UP -> { lastX = event.getX(if (event.actionIndex==0) 1 else 0); lastY = event.getY(if (event.actionIndex==0) 1 else 0) }
            MotionEvent.ACTION_UP -> {
                if(selecting) {
                    if(selectionTool in setOf("wand","color")) { if(tap) { val p=imagePoint(event.x,event.y);onSelectionPoint?.invoke(p.x.toInt(),p.y.toInt()) } }
                    else onSelectionShape?.invoke(selectionTool!!,stroke.map { PointF(it.x,it.y) })
                }
                selecting=false;stroke.clear();invalidate()
                if(tap) { performClick();if(editingEnabled && selectionTool==null && onLayerPick!=null) onLayerPick?.invoke(((event.x-imageRect.left)*bitmap!!.width/imageRect.width()).toInt(),((event.y-imageRect.top)*bitmap!!.height/imageRect.height()).toInt()) }
                else if(movingLayer && (layerDx.roundToInt()!=0 || layerDy.roundToInt()!=0)) onLayerMove?.invoke(layerDx.roundToInt(),layerDy.roundToInt())
                movingLayer=false;layerDx=0f;layerDy=0f;invalidate();drag=-1;parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { selecting=false;stroke.clear();movingLayer=false;layerDx=0f;layerDy=0f;invalidate();drag = -1; parent.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
