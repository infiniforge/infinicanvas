package com.infinicanvas

import android.content.Context
import android.view.View
import android.widget.GridLayout

/** Dense, row-major palette with native accessible/focusable cells. No color labels outside the grid. */
class PaletteGrid(context:Context,colors:List<Int>,columns:Int,selectedIndex:Int=-1,selected:(Int)->Unit):GridLayout(context) {
    private var lastCell=-1
    init {
        columnCount=columns
        for((index,argb) in colors.withIndex()) addView(EditorThumbnail(context,swatch=argb,index=index).apply {
            isClickable=true;isFocusable=true;isSelected=index==selectedIndex
            contentDescription="Edit palette entry $index, RGB ${String.format("%06X",argb and 0xffffff)}"
            setOnClickListener { selectIndex(index);selected(index) }
        })
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun selectIndex(index:Int) { for(i in 0 until childCount) getChildAt(i).isSelected=i==index }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val available=MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight
        val cell=(available/columnCount).coerceAtLeast(1)
        if(lastCell!=cell) {
            lastCell=cell
            for(i in 0 until childCount) getChildAt(i).layoutParams=LayoutParams().apply {
                width=cell;height=cell;rowSpec=spec(i/columnCount);columnSpec=spec(i%columnCount)
            }
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
