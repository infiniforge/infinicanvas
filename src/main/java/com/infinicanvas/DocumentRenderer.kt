package com.infinicanvas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.infinifolder.preview.*
import java.io.File

object DocumentRenderer {
    data class Result(val document:LayerDocument,val bitmap:Bitmap,val colors:List<Int>,val cycles:List<PaletteDocument.Cycle>,
        val paletteBytes:ByteArray?,val palettePreservable:Boolean,val animation:BitmapAnimation?,val layered:Boolean,val frames:AnimationDocument?=null,val delays:Map<String,Int> = emptyMap(),val framePreviews:Map<String,Bitmap> = emptyMap(),val ora:OpenRasterDocument?=null,val drawable:android.graphics.drawable.Drawable?=null,val project:AnimationProject?=null)

    @JvmOverloads
    fun render(base:Bitmap,baseLayers:LayerDocument?,palette:PaletteDocument?,edits:List<Edit>,assets:File,baseAnimation:AnimationDocument?=null,baseOra:OpenRasterDocument?=null,baseProject:AnimationProject?=null):Result {
        if((baseProject!=null || baseAnimation!=null) && (baseAnimation==null || edits.filterIsInstance<Edit.FrameMode>().lastOrNull()?.enabled!=false))
            return TimelineRenderer.render(baseProject ?: AnimationProjects.fromAnimation(baseAnimation!!),edits,assets)
        val colors=palette?.colors?.toMutableList() ?: mutableListOf()
        val ranges=palette?.cycles?.toMutableList() ?: mutableListOf()
        for(edit in edits) when(edit) {
            is Edit.PaletteColor -> { require(edit.index<palette!!.editableColors); colors[edit.index]=edit.argb }
            is Edit.PaletteShift -> {
                require(edit.last<palette!!.editableColors)
                val old=colors.subList(edit.first,edit.last+1).toList(); val count=old.size
                for(i in old.indices) colors[edit.first+i]=old[Math.floorMod(i-edit.steps,count)]
            }
            is Edit.CycleChange -> {
                require(palette?.format=="iff" && edit.last<palette.editableColors && edit.index<=ranges.size)
                val value=PaletteDocument.Cycle(edit.first,edit.last,edit.millihertz,edit.reverse,edit.enabled)
                if(edit.index==ranges.size) ranges+=value else ranges[edit.index]=value
            }
            else -> Unit
        }
        val encoded=palette?.encode(colors,ranges)
        val preserve=palette!=null && edits.all { it is Edit.PaletteColor || it is Edit.PaletteShift || it is Edit.CycleChange }
        var animation:BitmapAnimation?=null
        val initial=if(encoded!=null) {
            val uri=Uri.parse("infinicanvas://draft/image.${palette.format}")
            val frame=ThumbnailDecoder(null) { encoded.inputStream() }.decodeEditorFrame(uri,palette.format)
                ?: error("Could not render the indexed image")
            if(preserve) {
                val preview=ThumbnailDecoder(null) { encoded.inputStream() }.decodeViewerFrame(uri,512,palette.format)
                animation=preview?.colorCycling ?: preview?.animation
                preview?.close()
            }
            frame.use { it.takeBitmap() }
        } else base
        var ora=baseOra
        var doc=baseOra?.document ?: baseLayers ?: LayerDocument(initial.width,initial.height,listOf(ImageLayer("layer-0","Background",initial)))
        var layered=baseLayers!=null || baseOra!=null
        var frameMode=baseAnimation!=null
        val delays=mutableMapOf<String,Int>()
        baseAnimation?.frames?.forEachIndexed { i,f -> delays["frame-$i"]=f.delayMillis }
        fun update(id:String,change:(ImageLayer)->ImageLayer) {
            require(doc.layers.any { it.id==id }) { "Layer is no longer available" }
            doc=doc.copy(layers=doc.layers.map { if(it.id==id) change(it) else it })
        }
        fun rasterized(id:String) {
            ora?.assets?.get(id)?.let { asset ->
                require(asset.renderable) { "${asset.reference.path} is retained, but no visual decoder is available for pixel editing" }
                ora=ora!!.copy(assets=ora!!.assets+(id to asset.copy(rasterized=true)))
            }
        }
        fun paletteEdit(layer:ImageLayer,edit:Edit):ImageLayer {
            val asset=ora?.assets?.get(layer.id) ?: error("No embedded palette")
            val source=asset.editablePalette ?: error("This layer has no editable source palette")
            val colors=source.colors.toMutableList();val cycles=source.cycles.toMutableList()
            when(edit) {
                is Edit.PaletteColor -> { require(edit.index<source.editableColors);colors[edit.index]=edit.argb }
                is Edit.PaletteShift -> { require(edit.last<source.editableColors);val old=colors.subList(edit.first,edit.last+1).toList();for(i in old.indices) colors[edit.first+i]=old[Math.floorMod(i-edit.steps,old.size)] }
                is Edit.CycleChange -> { require(source.format=="iff" && edit.last<source.editableColors && edit.index<=cycles.size)
                    val cycle=PaletteDocument.Cycle(edit.first,edit.last,edit.millihertz,edit.reverse,edit.enabled)
                    if(edit.index==cycles.size) cycles+=cycle else cycles[edit.index]=cycle }
                else -> error("Not a palette edit")
            }
            val encoded=source.encode(colors,cycles)
            val decoded=OpenRasterDocuments.decodeAsset(asset.reference,encoded)
            ora=ora!!.copy(assets=ora!!.assets+(layer.id to decoded.second))
            return layer.copy(bitmap=decoded.first.bitmap)
        }
        fun raster(bitmap:Bitmap,edit:Edit):Bitmap {
            if(edit !is Edit.ClearPixels) return ImageRenderer.render(bitmap,listOf(edit))
            val encoded=BitmapFactory.decodeFile(File(assets,edit.file).path) ?: error("Selection mask is missing")
            try {
                require(encoded.width==edit.width && encoded.height==edit.height)
                return PixelSelections.clear(bitmap,PixelSelections.fromBitmap(encoded))
            } finally { encoded.recycle() }
        }
        for(edit in edits) {
            when(edit) {
                is Edit.CropImage -> {
                    require(edit.x+edit.width<=doc.width && edit.y+edit.height<=doc.height) { "Crop exceeds canvas pixels" }
                    doc=if(!layered && doc.layers.size==1) {
                        val bitmap=raster(doc.layers.single().bitmap,Edit.PixelCrop(edit.x,edit.y,edit.width,edit.height))
                        LayerDocument(edit.width,edit.height,listOf(doc.layers.single().copy(bitmap=bitmap)))
                    } else doc.copy(width=edit.width,height=edit.height,layers=doc.layers.map { it.copy(x=Math.subtractExact(it.x,edit.x),y=Math.subtractExact(it.y,edit.y)) })
                }
                is Edit.ScaleImage -> {
                    val sx=edit.width.toDouble()/doc.width;val sy=edit.height.toDouble()/doc.height
                    val sizes=doc.layers.map { if(ora?.assets?.get(it.id)?.renderable==false) it.bitmap.width to it.bitmap.height else maxOf(1,kotlin.math.round(it.bitmap.width*sx).toInt()) to maxOf(1,kotlin.math.round(it.bitmap.height*sy).toInt()) }
                    sizes.forEach { PixelGeometry.validate(it.first,it.second) }
                    val positions=doc.layers.map { kotlin.math.round(it.x*sx).toInt() to kotlin.math.round(it.y*sy).toInt() }
                    require(doc.layers.all { it.x*sx in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() && it.y*sy in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }) { "Scaled layer coordinate overflow" }
                    val layers=doc.layers.mapIndexed { i,layer ->
                        val bitmap=if(ora?.assets?.get(layer.id)?.renderable==false) layer.bitmap else { rasterized(layer.id);ImageRenderer.render(layer.bitmap,listOf(Edit.Resize(sizes[i].first,sizes[i].second,edit.smooth))) }
                        layer.copy(bitmap=bitmap,
                        x=positions[i].first,y=positions[i].second) }
                    doc=doc.copy(width=edit.width,height=edit.height,layers=layers)
                }
                is Edit.FrameMode -> {
                    if(edit.enabled) {
                        val frames=doc.layers.filter { it.visible }.map { layer -> AnimationProjectFrame(layer.id,layer.name,
                            (ora?.copy(document=doc.copy(layers=listOf(layer)),assets=ora!!.assets.filterKeys { it==layer.id }) ?: OpenRasterDocuments.fromDocument(doc.copy(layers=listOf(layer)))),delays[layer.id] ?: 100) }
                        return TimelineRenderer.render(AnimationProject(doc.width,doc.height,frames),edits.drop(edits.indexOf(edit)+1),assets)
                    }
                    frameMode=false;layered=true
                }
                is Edit.FrameDelay -> { require(doc.layers.any { it.id==edit.id }); delays[edit.id]=edit.millis }
                is Edit.PaletteColor,is Edit.PaletteShift,is Edit.CycleChange -> Unit
                is Edit.LayerProperties -> { layered=true; update(edit.id) { it.copy(name=edit.name,visible=edit.visible,opacity=edit.opacity,x=edit.x,y=edit.y,blend=edit.blend) } }
                is Edit.LayerMove -> {
                    layered=true; val list=doc.layers.toMutableList(); val old=list.indexOfFirst { it.id==edit.id }; require(old>=0)
                    val destination=list[edit.position.coerceIn(0,list.lastIndex)]
                    if(ora!=null) require((ora!!.assets[edit.id]?.reference?.parent ?: 0)==(ora!!.assets[destination.id]?.reference?.parent ?: 0)) { "Reorder media within its group; cross-group reparenting is not yet available" }
                    val layer=list.removeAt(old); list.add(edit.position.coerceIn(0,list.size),layer); doc=doc.copy(layers=list)
                }
                is Edit.LayerDuplicate -> {
                    layered=true; val index=doc.layers.indexOfFirst { it.id==edit.id }; require(index>=0)
                    delays[edit.newId]=delays[edit.id] ?: 100
                    ora?.assets?.get(edit.id)?.let { asset -> ora=ora!!.copy(assets=ora!!.assets+(edit.newId to asset.copy(reference=asset.reference.copy(id=edit.newId)))) }
                    val list=doc.layers.toMutableList(); list.add(index,list[index].copy(id=edit.newId,name=list[index].name+" copy")); doc=doc.copy(layers=list)
                }
                is Edit.LayerDelete -> { require(doc.layers.size>1); layered=true; doc=doc.copy(layers=doc.layers.filter { it.id!=edit.id }); require(doc.layers.isNotEmpty()) }
                is Edit.LayerNew -> {
                    layered=true; val bitmap=Bitmap.createBitmap(doc.width,doc.height,Bitmap.Config.ARGB_8888)
                    doc=doc.copy(layers=listOf(ImageLayer(edit.id,edit.name,bitmap))+doc.layers)
                }
                is Edit.MediaImport -> {
                    require(edit.file.matches(Regex("asset-[a-f0-9-]+\\.bin")))
                    val bytes=File(assets,edit.file).readBytes();val ext=edit.extension.takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "bin"
                    val ref=OpenRasterReference(edit.id,"data/${edit.id}.$ext",edit.name,0,0,255,true,"normal",0,0,0,null,edit.mime)
                    val decoded=OpenRasterDocuments.decodeAsset(ref,bytes)
                    ora=(ora ?: OpenRasterDocuments.fromDocument(doc)).let { it.copy(assets=it.assets+(edit.id to decoded.second)) }
                    layered=true;doc=doc.copy(layers=listOf(decoded.first)+doc.layers)
                }
                is Edit.LayerImport -> {
                    require(edit.file.matches(Regex("asset-[a-f0-9-]+\\.png")))
                    val bitmap=BitmapFactory.decodeFile(File(assets,edit.file).path) ?: error("Imported layer pixels are missing")
                    layered=true; doc=doc.copy(layers=listOf(ImageLayer(edit.id,edit.name,bitmap))+doc.layers)
                }
                is Edit.OnLayer -> {
                    layered=true; update(edit.id) { layer ->
                        if(edit.operation is Edit.PaletteColor || edit.operation is Edit.PaletteShift || edit.operation is Edit.CycleChange) return@update paletteEdit(layer,edit.operation)
                        rasterized(layer.id)
                        val changed=raster(layer.bitmap,edit.operation)
                        val x=when(val op=edit.operation) { is Edit.PixelCrop -> Math.addExact(layer.x,op.x); is Edit.Crop -> layer.x+(op.left*layer.bitmap.width).toInt(); is Edit.Turn -> layer.x+(layer.bitmap.width-changed.width)/2; else -> layer.x }
                        val y=when(val op=edit.operation) { is Edit.PixelCrop -> Math.addExact(layer.y,op.y); is Edit.Crop -> layer.y+(op.top*layer.bitmap.height).toInt(); is Edit.Turn -> layer.y+(layer.bitmap.height-changed.height)/2; else -> layer.y }
                        layer.copy(bitmap=changed,x=x,y=y)
                    }
                }
                else -> {
                    require(!layered && doc.layers.size==1) { "Choose a layer for this edit" }
                    val bitmap=raster(doc.layers.single().bitmap,edit)
                    doc=LayerDocument(bitmap.width,bitmap.height,listOf(doc.layers.single().copy(bitmap=bitmap)))
                }
            }
            if(ora!=null) OpenRasterDocuments.validate(ora!!.copy(document=doc)) else LayerDocuments.validate(doc)
        }
        val framePreviews=if(frameMode) doc.layers.associate { layer -> layer.id to
            if(layer.x==0 && layer.y==0 && layer.opacity==255 && layer.blend=="normal" && layer.bitmap.width==doc.width && layer.bitmap.height==doc.height) layer.bitmap
            else LayerDocuments.composite(doc.copy(layers=listOf(layer.copy(visible=true))))
        } else emptyMap()
        val frames=if(frameMode) AnimationDocument(doc.width,doc.height,doc.layers.filter { it.visible }.map { layer ->
            AnimationFrame(framePreviews.getValue(layer.id),delays[layer.id] ?: 100)
        },baseAnimation?.plays ?: 0).also(AnimationDocuments::validate) else null
        if(frames!=null) animation=AnimationDocuments.player(frames)
        ora=ora?.copy(document=doc)
        val drawable=if(!frameMode && ora!=null) OpenRasterDocuments.playback(ora!!) else null
        val bitmap=if(ora!=null && frames==null) OpenRasterDocuments.composite(ora!!) else if(frames!=null) frames.frames.first().bitmap else if(!layered && doc.layers.size==1) doc.layers.single().bitmap else LayerDocuments.composite(doc)
        return Result(doc,bitmap,colors,ranges,encoded,preserve,animation,layered,frames,delays,framePreviews,ora,drawable)
    }
}
