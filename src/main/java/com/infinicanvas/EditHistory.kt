package com.infinicanvas

import com.infinifolder.preview.PixelGeometry

/** App-owned, replayable edits. Crop coordinates are relative to the image at that step. */
sealed interface Edit {
    data class PixelCrop(val x:Int,val y:Int,val width:Int,val height:Int):Edit {
        init { PixelGeometry.validateCrop(x,y,width,height) }
    }
    data class CropImage(val x:Int,val y:Int,val width:Int,val height:Int):Edit { init { PixelGeometry.validateCrop(x,y,width,height) } }
    data class ScaleImage(val width:Int,val height:Int,val smooth:Boolean=true):Edit { init { PixelGeometry.validate(width,height) } }
    data class ClearPixels(val file:String,val width:Int,val height:Int):Edit {
        init { require(file.matches(Regex("asset-[a-f0-9-]+\\.png")));PixelGeometry.validate(width,height) }
    }
    /** Null frameId applies atomically to all frames; layerId is only used for a single frame. */
    data class FrameOperation(val frameId:String?,val operation:Edit,val layerId:String?=null):Edit {
        init { require(frameId!=null || layerId==null);require(operation is Turn || operation is Flip || operation is Crop || operation is PixelCrop || operation is CropImage || operation is Resize || operation is ScaleImage || operation is Adjust || operation is ClearPixels || operation is PaletteColor || operation is PaletteShift || operation is CycleChange) }
    }
    data class FrameEdit(val id:String,val operation:Edit):Edit { init { require(operation !is FrameEdit && operation !is FrameMode) } }
    data class FrameLoops(val plays:Int):Edit { init { require(plays>=0) } }
    data class FrameMode(val enabled:Boolean) : Edit
    data class FrameDelay(val id:String,val millis:Int) : Edit { init { require(millis in 1..65535000) } }
    data class PaletteColor(val index: Int, val argb: Int) : Edit { init { require(index in 0..255) } }
    data class PaletteShift(val first: Int, val last: Int, val steps: Int) : Edit {
        init { require(first in 0..255 && last in first..255 && steps in -256..256) }
    }
    data class CycleChange(val index: Int, val first: Int, val last: Int, val millihertz: Int,
        val reverse: Boolean, val enabled: Boolean) : Edit {
        init { require(index in 0..255 && first in 0..255 && last in first..255 && millihertz in 1..239000) }
    }
    data class LayerProperties(val id: String, val name: String, val visible: Boolean,
        val opacity: Int, val x: Int, val y: Int, val blend: String) : Edit {
        init { require(opacity in 0..255 && name.isNotBlank()) }
    }
    data class LayerMove(val id: String, val position: Int) : Edit
    data class LayerDuplicate(val id: String, val newId: String) : Edit
    data class LayerDelete(val id: String) : Edit
    data class LayerNew(val id: String, val name: String) : Edit
    data class MediaImport(val id:String,val file:String,val name:String,val extension:String,val mime:String?=null):Edit
    data class LayerImport(val id: String, val file: String, val name: String) : Edit
    data class OnLayer(val id: String, val operation: Edit) : Edit {
        init { require(operation is Turn || operation is Flip || operation is Crop || operation is Resize || operation is Adjust || operation is PixelCrop || operation is ClearPixels || operation is PaletteColor || operation is PaletteShift || operation is CycleChange) }
    }
    data class Turn(val clockwise: Boolean = true) : Edit
    data class Flip(val horizontal: Boolean = true) : Edit
    data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) : Edit {
        init {
            require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
            require(right > left && bottom > top)
        }
    }
    data class Resize @JvmOverloads constructor(val width: Int, val height: Int,val smooth:Boolean=true) : Edit {
        init { PixelGeometry.validate(width,height) }
    }
    data class Adjust(val brightness: Int = 0, val contrast: Int = 0, val saturation: Int = 0) : Edit {
        init { require(listOf(brightness, contrast, saturation).all { it in -100..100 }) }
        val isIdentity get() = brightness == 0 && contrast == 0 && saturation == 0
    }
}

data class EditHistory(val edits: List<Edit> = emptyList(), val cursor: Int = edits.size) {
    init { require(cursor in 0..edits.size) }
    val active get() = edits.take(cursor)
    val canUndo get() = cursor > 0
    val canRedo get() = cursor < edits.size
    fun append(edit: Edit): EditHistory {
        return EditHistory(active + edit)
    }
    fun undo() = if(canUndo) copy(cursor=cursor-1) else this
    fun redo() = if(canRedo) copy(cursor=cursor+1) else this
}
