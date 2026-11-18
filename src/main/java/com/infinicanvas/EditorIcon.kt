package com.infinicanvas

import android.content.Context
import android.graphics.*
import android.view.View

/** Small vector artwork in a full 48 dp touch target. Labels stay available to TalkBack and long press. */
class EditorIcon(context:Context,var symbol:String,label:String):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable=true;isFocusable=true;contentDescription=label;tooltipText=label }
    fun label(value:String) { contentDescription=value;tooltipText=value }
    override fun drawableStateChanged() { super.drawableStateChanged();invalidate() }
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        val d=resources.displayMetrics.density
        if(isSelected || isPressed || isFocused) {
            p.style=Paint.Style.FILL;p.color=context.getColor(if(isSelected) R.color.accent else R.color.surface_secondary)
            c.drawRoundRect(width/2f-19*d,height/2f-19*d,width/2f+19*d,height/2f+19*d,10*d,10*d,p)
        }
        c.save();c.translate(width/2f-12*d,height/2f-12*d);c.scale(d,d)
        p.color=context.getColor(if(isSelected) R.color.on_accent else R.color.text_primary)
        p.alpha=if(isEnabled) 255 else 85;p.style=Paint.Style.STROKE;p.strokeWidth=1.8f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        fun line(vararg xy:Float) { val path=Path();path.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2) path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p) }
        fun box(l:Float,t:Float,r:Float,b:Float)=c.drawRoundRect(l,t,r,b,1f,1f,p)
        fun arrow() { c.drawArc(5f,4f,21f,20f,205f,285f,false,p);line(3f,4f,3f,10f,9f,10f) }
        when(symbol) {
            "open" -> {line(2f,8f,2f,5f,9f,5f,11f,8f,22f,8f);line(2f,8f,22f,8f,19f,20f,2f,20f,2f,8f)}
            "new" -> {box(4f,3f,20f,21f);line(8f,12f,16f,12f);line(12f,8f,12f,16f)}
            "export" -> {line(4f,15f,4f,21f,20f,21f,20f,15f);line(12f,16f,12f,3f);line(7f,8f,12f,3f,17f,8f)}
            "undo" -> {line(3f,5f,3f,11f,9f,11f);val path=Path();path.moveTo(3f,11f);path.cubicTo(12f,0f,26f,11f,18f,20f);c.drawPath(path,p)}
            "redo" -> {c.save();c.scale(-1f,1f,12f,12f);line(3f,5f,3f,11f,9f,11f);val path=Path();path.moveTo(3f,11f);path.cubicTo(12f,0f,26f,11f,18f,20f);c.drawPath(path,p);c.restore()}
            "more" -> {p.style=Paint.Style.FILL;for(y in listOf(5f,12f,19f)) c.drawCircle(12f,y,1.7f,p)}
            "fit" -> {line(3f,9f,3f,3f,9f,3f);line(15f,3f,21f,3f,21f,9f);line(21f,15f,21f,21f,15f,21f);line(9f,21f,3f,21f,3f,15f);box(8f,8f,16f,16f)}
            "select","rect","selectAll" -> {
                p.pathEffect=DashPathEffect(floatArrayOf(3f,2f),0f);box(3f,3f,21f,21f);p.pathEffect=null
                if(symbol=="selectAll") { line(8f,12f,16f,12f);line(12f,8f,12f,16f) }
            }
            "ellipse" -> { p.pathEffect=DashPathEffect(floatArrayOf(3f,2f),0f);c.drawOval(2f,5f,22f,19f,p);p.pathEffect=null }
            "lasso" -> {val path=Path();path.moveTo(8f,18f);path.cubicTo(-4f,7f,8f,-2f,19f,6f);path.cubicTo(30f,15f,11f,25f,8f,14f);c.drawPath(path,p);line(8f,18f,5f,23f)}
            "wand" -> {line(4f,21f,17f,8f);line(10f,15f,7f,12f);line(17f,1f,17f,4f);line(22f,6f,20f,6f);line(12f,4f,10f,2f)}
            "color" -> {c.drawCircle(8f,8f,5f,p);c.drawCircle(16f,8f,5f,p);c.drawCircle(12f,16f,5f,p)}
            "background" -> {box(2f,2f,22f,22f);line(2f,9f,7f,9f);line(2f,15f,7f,15f);line(17f,9f,22f,9f);line(17f,15f,22f,15f);c.drawCircle(12f,12f,4f,p)}
            "clear" -> {line(3f,14f,14f,3f,22f,11f,12f,21f,9f,21f,3f,14f);line(8f,9f,16f,17f);line(15f,21f,22f,21f)}
            "invert" -> {c.drawCircle(12f,12f,9f,p);p.style=Paint.Style.FILL;c.drawArc(3f,3f,21f,21f,90f,180f,true,p);p.style=Paint.Style.STROKE}
            "deselect" -> {box(3f,3f,21f,21f);line(4f,20f,20f,4f)}
            "pixel" -> {p.style=Paint.Style.FILL;p.textSize=12f;p.textAlign=Paint.Align.CENTER;c.drawText("1:1",12f,16f,p);p.style=Paint.Style.STROKE}
            "crop" -> {line(7f,2f,7f,17f,22f,17f);line(2f,7f,17f,7f,17f,22f);line(20f,4f,11f,13f)}
            "transform","restart","rotateLeft" -> arrow()
            "rotateRight" -> {c.save();c.scale(-1f,1f,12f,12f);arrow();c.restore()}
            "flipH","flipV" -> {if(symbol=="flipV") {c.rotate(90f,12f,12f)};line(12f,2f,12f,22f);line(8f,6f,3f,18f,8f,18f,8f,6f);line(16f,6f,21f,18f,16f,18f,16f,6f)}
            "resize" -> {box(4f,4f,20f,20f);line(9f,15f,17f,7f);line(12f,7f,17f,7f,17f,12f)}
            "adjust","properties" -> {for(x in listOf(5f,12f,19f)) line(x,3f,x,21f);for((x,y) in listOf(5f to 8f,12f to 16f,19f to 10f)) {p.style=Paint.Style.FILL;c.drawCircle(x,y,2.8f,p);p.style=Paint.Style.STROKE}}
            "palette" -> {for(y in listOf(3f,14f)) for(x in listOf(3f,14f)) box(x,y,x+7,y+7)}
            "cycles" -> {c.drawArc(3f,3f,21f,21f,210f,140f,false,p);c.drawArc(3f,3f,21f,21f,30f,140f,false,p);line(17f,3f,21f,8f,15f,8f);line(7f,21f,3f,16f,9f,16f)}
            "layers" -> {line(2f,8f,12f,3f,22f,8f,12f,13f,2f,8f);line(2f,13f,12f,18f,22f,13f);line(2f,18f,12f,23f,22f,18f)}
            "frames" -> {box(2f,4f,22f,20f);line(7f,4f,7f,20f);line(17f,4f,17f,20f);line(2f,9f,7f,9f);line(2f,15f,7f,15f);line(17f,9f,22f,9f);line(17f,15f,22f,15f)}
            "play" -> {p.style=Paint.Style.FILL;val path=Path();path.moveTo(7f,3f);path.lineTo(21f,12f);path.lineTo(7f,21f);path.close();c.drawPath(path,p)}
            "pause" -> {p.style=Paint.Style.FILL;c.drawRect(6f,4f,10f,20f,p);c.drawRect(15f,4f,19f,20f,p)}
            "close" -> {line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
            "check" -> line(4f,12f,10f,18f,21f,6f)
            "up","down","left","right" -> {c.save();c.rotate(when(symbol){"down"->180f;"left"->270f;"right"->90f;else->0f},12f,12f);line(5f,10f,12f,3f,19f,10f);line(12f,3f,12f,21f);c.restore()}
            "duplicate" -> {box(8f,8f,21f,21f);line(16f,4f,4f,4f,4f,16f)}
            "delete" -> {line(3f,6f,21f,6f);line(9f,3f,15f,3f);line(6f,6f,7f,21f,17f,21f,18f,6f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
            "import" -> {box(2f,3f,16f,21f);line(5f,16f,9f,11f,13f,15f);line(16f,10f,22f,10f);line(19f,7f,22f,10f,19f,13f)}
            "duration" -> {c.drawCircle(12f,12f,9f,p);line(12f,6f,12f,12f,17f,15f)}
            "index" -> {line(8f,3f,6f,21f);line(18f,3f,16f,21f);line(3f,9f,22f,9f);line(2f,16f,21f,16f)}
            "aspect" -> {box(2f,5f,22f,19f);line(8f,5f,8f,19f)}
            "info" -> {c.drawCircle(12f,12f,9f,p);line(12f,11f,12f,17f);p.style=Paint.Style.FILL;c.drawCircle(12f,7f,1f,p)}
        }
        p.alpha=255;c.restore()
    }
    override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if(isClickable) info.className="android.widget.Button"
    }
    override fun performClick():Boolean { super.performClick();return true }
}
