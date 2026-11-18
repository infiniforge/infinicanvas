package com.infinicanvas

import android.graphics.BitmapFactory
import com.infinifolder.preview.*
import java.io.File

/** Replays one frame/image operation without merging its layer graph. */
internal object FrameOperations {
    fun apply(source:OpenRasterDocument,operation:Edit,layerId:String?,assets:File,resizeCanvas:Boolean):OpenRasterDocument {
        fun render(input:OpenRasterDocument,edits:List<Edit>):OpenRasterDocument {
            val doc=input.document
            return DocumentRenderer.render(doc.layers.first().bitmap,doc,null,edits,assets,null,input).ora!!
        }
        if(layerId!=null && operation !is Edit.ScaleImage && operation !is Edit.CropImage)
            return render(source,listOf(Edit.OnLayer(layerId,operation)))
        val doc=source.document
        val visual=doc.layers.filter { source.assets[it.id]?.renderable!=false }
        when(operation) {
            is Edit.Resize -> return apply(source,Edit.ScaleImage(operation.width,operation.height,operation.smooth),null,assets,resizeCanvas)
            is Edit.PixelCrop -> return apply(source,Edit.CropImage(operation.x,operation.y,operation.width,operation.height),null,assets,resizeCanvas)
            is Edit.Crop -> {
                val x=(operation.left*doc.width).toInt();val y=(operation.top*doc.height).toInt()
                return apply(source,Edit.CropImage(x,y,maxOf(1,(operation.right*doc.width).toInt()-x),maxOf(1,(operation.bottom*doc.height).toInt()-y)),null,assets,resizeCanvas)
            }
            is Edit.ScaleImage -> {
                val result=render(source,listOf(operation))
                return if(resizeCanvas) result else result.copy(document=result.document.copy(width=doc.width,height=doc.height))
            }
            is Edit.CropImage -> {
                var result=render(source,listOf(operation))
                if(resizeCanvas) return result
                // A single frame keeps the shared animation canvas, with the cropped contents at its origin.
                for(layer in result.document.layers.toList()) {
                    if(result.assets[layer.id]?.renderable==false) continue
                    val x=maxOf(0,layer.x);val y=maxOf(0,layer.y)
                    val right=minOf(operation.width.toLong(),layer.x.toLong()+layer.bitmap.width).toInt()
                    val bottom=minOf(operation.height.toLong(),layer.y.toLong()+layer.bitmap.height).toInt()
                    if(right<=x || bottom<=y) result=result.copy(document=result.document.copy(layers=result.document.layers.map { if(it.id==layer.id) it.copy(visible=false) else it }))
                    else if(x!=layer.x || y!=layer.y || right.toLong()!=layer.x.toLong()+layer.bitmap.width || bottom.toLong()!=layer.y.toLong()+layer.bitmap.height)
                        result=render(result,listOf(Edit.OnLayer(layer.id,Edit.PixelCrop(Math.subtractExact(x,layer.x),Math.subtractExact(y,layer.y),right-x,bottom-y))))
                }
                return result.copy(document=result.document.copy(width=doc.width,height=doc.height))
            }
            is Edit.Turn,is Edit.Flip -> {
                val result=render(source,visual.map { Edit.OnLayer(it.id,operation) })
                val positions=doc.layers.associate { layer ->
                    val x=layer.x;val y=layer.y;val w=layer.bitmap.width;val h=layer.bitmap.height
                    layer.id to when(operation) {
                        is Edit.Turn -> if(operation.clockwise) Math.subtractExact(Math.subtractExact(doc.height,y),h) to x else y to Math.subtractExact(Math.subtractExact(doc.width,x),w)
                        is Edit.Flip -> if(operation.horizontal) Math.subtractExact(Math.subtractExact(doc.width,x),w) to y else x to Math.subtractExact(Math.subtractExact(doc.height,y),h)
                        else -> error("Unreachable")
                    }
                }
                return result.copy(document=result.document.copy(width=if(resizeCanvas && operation is Edit.Turn) doc.height else doc.width,
                    height=if(resizeCanvas && operation is Edit.Turn) doc.width else doc.height,
                    layers=result.document.layers.map { val position=positions.getValue(it.id);it.copy(x=position.first,y=position.second) }))
            }
            is Edit.ClearPixels -> {
                val encoded=BitmapFactory.decodeFile(File(assets,operation.file).path) ?: error("Selection mask is missing")
                val mask=try { PixelSelections.fromBitmap(encoded).also { require(it.width==operation.width && it.height==operation.height) } } finally { encoded.recycle() }
                val updated=source.assets.toMutableMap()
                val layers=doc.layers.map { layer ->
                    if(source.assets[layer.id]?.renderable==false) layer else {
                        val coverage=ByteArray(layer.bitmap.width*layer.bitmap.height)
                        for(y in 0 until layer.bitmap.height) {
                            val sy=y.toLong()+layer.y;if(sy !in 0L until mask.height.toLong()) continue
                            val start=maxOf(0L,-layer.x.toLong());val end=minOf(layer.bitmap.width.toLong(),mask.width.toLong()-layer.x)
                            if(start<end) mask.coverage.copyInto(coverage,y*layer.bitmap.width+start.toInt(),(sy*mask.width+start+layer.x).toInt(),(sy*mask.width+end+layer.x).toInt())
                        }
                        updated[layer.id]?.let { updated[layer.id]=it.copy(rasterized=true) }
                        layer.copy(bitmap=PixelSelections.clear(layer.bitmap,PixelSelection(layer.bitmap.width,layer.bitmap.height,coverage)))
                    }
                }
                return source.copy(document=doc.copy(layers=layers),assets=updated)
            }
            is Edit.PaletteColor,is Edit.PaletteShift,is Edit.CycleChange -> {
                val indexed=visual.filter { source.assets[it.id]?.editablePalette!=null }
                require(indexed.isNotEmpty()) { "A target frame has no editable palette. Turn off Apply to all frames to edit only this frame." }
                return render(source,indexed.map { Edit.OnLayer(it.id,operation) })
            }
            is Edit.Adjust -> return render(source,visual.map { Edit.OnLayer(it.id,operation) })
            else -> error("Unsupported frame operation")
        }
    }
}
