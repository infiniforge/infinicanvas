package com.infinicanvas

import android.graphics.Bitmap
import com.infinifolder.preview.*
import java.io.File

/** An animation timeline owns frame documents. Image layers belong only to each frame document. */
internal object TimelineRenderer {
    fun render(base:AnimationProject,edits:List<Edit>,assets:File):DocumentRenderer.Result {
        var project=base
        fun frameEdit(id:String,operation:Edit) {
            require(project.frames.any { it.id==id }) { "Frame is no longer available" }
            project=project.copy(frames=project.frames.map { frame -> if(frame.id!=id) frame else {
                val source=frame.document
                val result=DocumentRenderer.render(source.document.layers.first().bitmap,source.document,null,listOf(operation),assets,null,source)
                frame.copy(document=result.ora ?: OpenRasterDocuments.fromDocument(result.document))
            } })
        }
        for(edit in edits) {
            when(edit) {
                is Edit.FrameOperation -> {
                    require(edit.frameId==null || project.frames.any { it.id==edit.frameId }) { "Frame is no longer available" }
                    val frames=project.frames.map { frame -> if(edit.frameId!=null && frame.id!=edit.frameId) frame else
                        frame.copy(document=FrameOperations.apply(frame.document,edit.operation,edit.layerId,assets,edit.frameId==null)) }
                    val canvas=if(edit.frameId==null) frames.first().document.document else null
                    project=project.copy(frames=frames,width=canvas?.width ?: project.width,height=canvas?.height ?: project.height)
                }
                is Edit.FrameEdit -> frameEdit(edit.id,edit.operation)
                is Edit.FrameLoops -> project=project.copy(plays=edit.plays)
                is Edit.FrameDelay -> { require(project.frames.any { it.id==edit.id });project=project.copy(frames=project.frames.map { if(it.id==edit.id) it.copy(delayMillis=edit.millis) else it }) }
                is Edit.LayerMove -> {
                    val frames=project.frames.toMutableList();val index=frames.indexOfFirst { it.id==edit.id };require(index>=0)
                    val frame=frames.removeAt(index);frames.add(edit.position.coerceIn(0,frames.size),frame);project=project.copy(frames=frames)
                }
                is Edit.LayerDuplicate -> {
                    val frames=project.frames.toMutableList();val index=frames.indexOfFirst { it.id==edit.id };require(index>=0)
                    frames.add(index+1,frames[index].copy(id=edit.newId,name=frames[index].name+" copy"));project=project.copy(frames=frames)
                }
                is Edit.LayerDelete -> { require(project.frames.size>1);require(project.frames.any { it.id==edit.id });project=project.copy(frames=project.frames.filter { it.id!=edit.id }) }
                is Edit.MediaImport,is Edit.LayerImport -> {
                    val name:String;val id:String;val file:String
                    when(edit) { is Edit.MediaImport -> { name=edit.name;id=edit.id;file=edit.file };is Edit.LayerImport -> { name=edit.name;id=edit.id;file=edit.file };else -> error("Unreachable") }
                    require(file.matches(Regex("asset-[a-f0-9-]+\\.(bin|png)")))
                    val bytes=File(assets,file).readBytes()
                    val ora=OpenRasterDocuments.decode(bytes) ?: run {
                        val extension=if(edit is Edit.MediaImport) edit.extension else "png"
                        val ref=OpenRasterReference("$id-pixels","data/$id.${extension.takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "bin"}",name,0,0,255,true,"normal",0,0,0,null)
                        val decoded=OpenRasterDocuments.decodeAsset(ref,bytes)
                        require(decoded.second.renderable) { "This media has no image renderer; import it into a frame’s layer editor to retain it" }
                        val document=LayerDocument(project.width,project.height,listOf(decoded.first))
                        OpenRasterDocuments.fromDocument(document).copy(assets=mapOf(decoded.first.id to decoded.second))
                    }
                    require(ora.document.width==project.width && ora.document.height==project.height) { "Imported ORA canvas must match the animation canvas" }
                    project=project.copy(frames=project.frames+AnimationProjectFrame(id,name,ora,100))
                }
                is Edit.ScaleImage,is Edit.CropImage -> {
                    val width=if(edit is Edit.ScaleImage) edit.width else (edit as Edit.CropImage).width
                    val height=if(edit is Edit.ScaleImage) edit.height else (edit as Edit.CropImage).height
                    project.frames.toList().forEach { frameEdit(it.id,edit) };project=project.copy(width=width,height=height)
                }
                // Old draft recipes addressed frame IDs through OnLayer. Preserve those calls without merging frame layers.
                is Edit.OnLayer -> {
                    val frame=project.frames.first { it.id==edit.id };require(frame.document.document.layers.size==1) { "Open frame layers to choose a layer" }
                    frameEdit(frame.id,Edit.OnLayer(frame.document.document.layers.first().id,edit.operation))
                }
                is Edit.FrameMode -> require(edit.enabled) { "Animations have independent frame documents; export a still to create an image" }
                else -> error("Open frame layers to edit the selected frame")
            }
            AnimationProjects.validate(project)
        }
        val animation=AnimationProjects.flatten(project)
        // Compatibility preview adapter only: these composites are never written as an ORA layer stack.
        val previews=project.frames.mapIndexed { i,frame -> ImageLayer(frame.id,frame.name,animation.frames[i].bitmap) }
        val doc=LayerDocument(project.width,project.height,previews)
        return DocumentRenderer.Result(doc,animation.frames.first().bitmap,emptyList(),emptyList(),null,false,
            AnimationDocuments.player(animation),true,animation,project.frames.associate { it.id to it.delayMillis },previews.associate { it.id to it.bitmap },project=project)
    }
}
