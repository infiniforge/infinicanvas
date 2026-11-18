package com.infinicanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.infinifolder.preview.*
import io.github.gretchycat.infiniselect.InfiniSelect
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.util.concurrent.Executors

/** Worker-owned document, encoded indexed source and layer graph, retained across recreation. */
class EditorController(context:Context) {
    private val app=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val folder=File(app.filesDir,"draft").apply { mkdirs() }
    private val session=AtomicFile(File(folder,"session.json"))
    private var document:Document?=null
    private var rendered:DocumentRenderer.Result?=null
    private var closed=false
    private var playing=false
    private var animationGeneration=0
    private var animationStart=0L
    private var animationOffset=0L
    private var playbackFinished=false
    var listener:(()->Unit)?=null
    var state=State()
        private set

    data class State(val bitmap:Bitmap?=null,val name:String="Your next canvas",val busy:Boolean=false,
        val message:String="Open an image or start a blank canvas.",val history:EditHistory=EditHistory(),
        val dirty:Boolean=false,val export:File?=null,val exportFormat:String="png",
        val width:Int=0,val height:Int=0,val layers:List<ImageLayer> = emptyList(),val layered:Boolean=false,val selectedLayer:String="layer-0",
        val colors:List<Int> = emptyList(),val editableColors:Int=0,val cycles:List<PaletteDocument.Cycle> = emptyList(),
        val paletteFormat:String?=null,val palettePreservable:Boolean=false,val canAnimate:Boolean=false,val playing:Boolean=false,
        val selection:PixelSelection?=null,val selectionOverlay:Bitmap?=null,val selectionBounds:android.graphics.Rect?=null,val selectedPixels:Int=0,
        val media:Map<String,OpenRasterAsset> = emptyMap(),val sourceFormat:String="png",val openFailure:String?=null,val openNotice:String?=null,val importNote:String?=null,val drawable:android.graphics.drawable.Drawable?=null,val frameMode:Boolean=false,val playbackFrame:String?=null,val loops:Int=0,val frameLayerMode:Boolean=false,val selectedFrame:String="frame-0",val frameItems:List<ImageLayer> = emptyList(),val delays:Map<String,Int> = emptyMap())
    private data class Document(val base:Bitmap,val file:File,val name:String,val source:String?,
        val layers:LayerDocument?=null,val palette:PaletteDocument?=null,val history:EditHistory=EditHistory(),
        val exported:List<Edit>?=emptyList(),val pendingFormat:String="png",
        val animation:AnimationDocument?=null,val playback:BitmapFrame?=null,val extension:String="png",val importNote:String?=null,val ora:OpenRasterDocument?=null,val project:AnimationProject?=null)

    init { task("Recovering your draft…") {
        if(session.baseFile.exists() || File(session.baseFile.path+".bak").exists()) {
            val j=session.openRead().bufferedReader().use { JSONObject(it.readText()) }
            require(j.getInt("version") in 1..3) { "Unsupported draft version" }
            val leaf=j.getString("base"); require(leaf.matches(Regex("base-[a-f0-9-]+\\.(png|bin)")))
            val file=File(folder,leaf)
            val kind=j.optString("kind","raster")
            val bytes=if(kind!="raster") file.readBytes() else null
            val extension=j.optString("extension","png")
            val project=if(kind=="project") AnimationProjects.decode(bytes!!) ?: error("Missing animation project") else null
            val ora=if(kind=="ora") OpenRasterDocuments.decode(bytes!!) ?: error("Missing ORA container draft") else null
            val animation=if(kind=="animation") AnimationDocuments.decode(bytes!!,Int.MAX_VALUE) ?: error("Missing animation draft") else null
            val playback=if(kind=="playback") decodeFrame(bytes!!,extension) else null
            val layers=ora?.document ?: animation?.let(::animationLayers) ?: if(kind=="layers") LayerDocuments.decode(bytes!!,Int.MAX_VALUE) ?: error("Missing layered draft") else null
            val palette=if(kind=="palette") PaletteDocument.read(bytes!!) ?: error("Missing indexed draft") else null
            val base=project?.frames?.first()?.document?.let(OpenRasterDocuments::composite) ?: animation?.frames?.first()?.bitmap ?: playback?.takeBitmap() ?: ora?.let(OpenRasterDocuments::composite) ?: layers?.let(LayerDocuments::composite) ?: if(palette!=null) decodeBytes(bytes!!,palette.format) else BitmapFactory.decodeFile(file.path)
            require(base!=null) { "Draft pixels are missing" }
            val candidate=Document(base,file,j.getString("name"),j.optString("source").takeIf { it.isNotBlank() },layers,palette,
                EditHistory(EditCodec.read(j.getJSONArray("edits")),j.getInt("cursor")),
                j.optJSONArray("exported")?.let(EditCodec::read),j.optString("pendingFormat","png"),animation,playback,extension,j.optString("importNote").takeIf { it.isNotBlank() },ora,project)
            val result=render(candidate); document=candidate; rendered=result
            post { state=state.copy(selectedLayer=j.optString("selectedLayer","layer-0"),selectedFrame=j.optString("selectedFrame",j.optString("selectedLayer","frame-0")),frameLayerMode=j.optBoolean("frameLayerMode",false)) }
            publish(candidate,result,"Draft recovered. Your source is unchanged.")
        } else post { state=state.copy(message="Open an image or start a blank canvas.") }
    } }

    fun open(content:Uri,authoritative:Uri,name:String)=openImage(content,authoritative.toString(),name,null)
    fun open(request:IncomingImage)=openImage(request.content,request.authoritative,request.name,request.mime)
    fun clearOpenNotice() { state=state.copy(openNotice=null) }
    fun clearOpenFailure() { state=state.copy(openFailure=null) }
    private fun openImage(content:Uri,authoritative:String?,nameHint:String?,mimeHint:String?)=task("Opening ${nameHint ?: "selected image"}…",reportOpenFailure=true) {
        val source=InfiniSelect.requireSelectedSource(app,content,authoritative,displayName=nameHint,mimeType=mimeHint)
        val name=sourceName(source.contentUri,source.authoritativeUri,nameHint ?: source.displayName)
        val mime=mimeHint ?: source.mimeType ?: runCatching { app.contentResolver.getType(source.contentUri) }.getOrNull()
        val prefix=app.contentResolver.openInputStream(source.contentUri)?.use { input ->
            val b=ByteArray(256); var at=0
            while(at<b.size) { val n=input.read(b,at,b.size-at); if(n<0) break; require(n>0); at+=n }; b.copyOf(at)
        } ?: error("No readable image stream")
        val signature=prefix.toString(Charsets.ISO_8859_1)
        val isLayers=signature.startsWith("8BPS") || signature.startsWith("gimp xcf ") || signature.startsWith("PK")
        val mayPalette=signature.startsWith("FORM") || (prefix.size>=8 && prefix[0]==(-119).toByte() && prefix[1]==80.toByte())
        val extension=ImageSignatures.extension(prefix) ?: name.substringAfterLast('.',"").lowercase().takeIf { it.isNotBlank() }
            ?: android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.orEmpty())
            ?: if(signature.startsWith("FORM")) "iff" else ""
        val mayAnimate=FlicDecoder.recognizes(prefix) || signature.startsWith("GIF8") || signature.startsWith("RIFF") || extension in setOf("apng","mng","fli","flc","avif","heif","heic")
        val notes=mutableListOf<String>()
        fun <T> optional(label:String,read:()->T?):T? = try { read() }
            catch(cancel:InterruptedIOException) { throw cancel }
            catch(denied:SecurityException) { throw denied }
            catch(error:Exception) { notes+="$label: ${error.message ?: "unsupported editable content"}";null }
            catch(_:OutOfMemoryError) { notes+="$label: insufficient memory for this import path; trying the rendered image.";null }
        val bytes=if(isLayers || mayPalette || mayAnimate) optional("Source retention") { readSource(source.contentUri) } else null
        val rawPalette=if(mayPalette && bytes!=null) optional("Palette editing") { PaletteDocument.read(bytes) } else null
        val animation=if((mayPalette || mayAnimate) && bytes!=null) optional("Frame editing") { AnimationDocuments.decode(bytes,Int.MAX_VALUE) } else null
        val palette=rawPalette.takeIf { animation==null }
        val project=if(signature.startsWith("PK") && bytes!=null) optional("Animation project") { AnimationProjects.decode(bytes) } else null
        val ora=if(project==null && signature.startsWith("PK") && bytes!=null) optional("ORA container") { OpenRasterDocuments.decode(bytes) } else null
        val layers=ora?.document ?: if(project==null && animation==null && isLayers && bytes!=null) optional("Layer editing") {
            LayerDocuments.decode(bytes,Int.MAX_VALUE).also { if(it==null) notes+="This project has no editable layer importer." }
        } else null
        val decoder=if(bytes!=null) ThumbnailDecoder(null) { bytes.inputStream() } else ThumbnailDecoder(app.contentResolver)
        val frame=if(project==null && animation==null && layers==null) optional("Animated viewer") { decoder.decodeEditorFrame(source.contentUri,extension,mime) }
            ?: decoder.decodeEditorImage(source.contentUri,extension,mime)?.let { BitmapFrame(it) } else null
        frame?.metadata?.warnings?.forEach { notes+="Image decoder: $it" }
        val base=project?.frames?.first()?.document?.let(OpenRasterDocuments::composite) ?: animation?.frames?.first()?.bitmap ?: ora?.let(OpenRasterDocuments::composite) ?: layers?.let(LayerDocuments::composite) ?: frame?.takeBitmap()
            ?: error("The library could not decode this image."+notes.takeIf { it.isNotEmpty() }?.joinToString("\n",prefix="\n").orEmpty())
        val playback=frame?.takeIf { it.animation!=null || it.animationDrawable!=null || it.colorCycling!=null }
        val retained=if(project!=null || animation!=null || layers!=null || palette!=null || playback!=null) bytes ?: readSource(source.contentUri) else null
        ora?.warnings?.let { notes.addAll(it) }
        val notice=notes.takeIf { it.isNotEmpty() }?.joinToString("\n",prefix=if(ora!=null) "ORA opened as separate media layers. Some layers have preview/editing limitations.\n\n" else "Opened as a rendered image. Some source structure is unavailable for editing.\n\n",postfix=if(ora!=null) "\n\nOriginal embedded media and extension metadata are retained for ORA saving." else "\n\nYour source is unchanged. Export saves the working image; unsupported source structure is not retained in that copy.")
        val note=when { project!=null -> "${project.frames.size} animation frames, each with its own ORA layers. Open Animation to edit."
            ora!=null -> "${ora.document.layers.size} separate media layers. ORA saves retain embedded files and extensions."
            animation!=null -> "${animation.frames.size} editable animation frames. Open Animation to edit timing, order and frame layers."
            layers!=null -> "${layers.layers.size} editable layers. Transform and Adjust affect the selected layer."
            palette!=null -> "${palette.editableColors} palette entries retained. Indexed export keeps original pixel data."
            playback!=null -> "Animation playback retained. Frame editing supports APNG and indexed FLI/FLC. Other animations retain playback."
            else -> "${base.width} × ${base.height} source pixels. Opened as a raster image." }
        install(base,name,source.authoritativeUri.toString(),false,retained,layers,palette,note,animation,playback,extension,notice,ora,project)
        if(notice!=null) post { state=state.copy(openNotice=notice) }
    }

    fun newCanvas(width:Int,height:Int,transparent:Boolean)=task("Creating canvas…") {
        PixelGeometry.validate(width,height)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if(transparent) Color.TRANSPARENT else Color.WHITE)
        install(bitmap,"Untitled.png",null,true,null,null,null,"New canvas · $width × $height pixels")
    }
    private fun install(base:Bitmap,name:String,source:String?,new:Boolean,bytes:ByteArray?,layers:LayerDocument?,palette:PaletteDocument?,message:String,animation:AnimationDocument?=null,playback:BitmapFrame?=null,extension:String="png",importNote:String?=null,ora:OpenRasterDocument?=null,project:AnimationProject?=null) {
        val file=File(folder,"base-${UUID.randomUUID()}.${if(bytes==null) "png" else "bin"}")
        try {
            if(bytes==null) writePng(base,file) else writeBytes(file,bytes)
            val next=Document(base,file,name,source,layers,palette,exported=if(new) null else emptyList(),animation=animation,playback=playback,extension=extension,importNote=importNote,ora=ora,project=project)
            val result=render(next); persist(next)
            document=next; rendered=result
            folder.listFiles()?.filter { (it.name.startsWith("base-") && it!=file) || it.name.startsWith("asset-") }?.forEach { it.delete() }
            publish(next,result,message)
        } catch(error:Throwable) { file.delete(); throw error }
    }
    private fun render(doc:Document)=DocumentRenderer.render(doc.base,doc.layers,doc.palette,doc.history.active,folder,doc.animation,doc.ora,doc.project ?: doc.animation?.let { AnimationProjects.fromAnimation(it).copy(attachments=mapOf("sources/original.${doc.extension}" to doc.file.readBytes())) })
    @JvmOverloads fun edit(edit:Edit,allFrames:Boolean=true) {
        val imageOperation=edit is Edit.Turn || edit is Edit.Flip || edit is Edit.Crop || edit is Edit.Resize || edit is Edit.Adjust || edit is Edit.PixelCrop || edit is Edit.ClearPixels || edit is Edit.CropImage || edit is Edit.ScaleImage || edit is Edit.PaletteColor || edit is Edit.PaletteShift || edit is Edit.CycleChange
        if(state.frameMode && imageOperation) {
            val layer=state.selectedLayer.takeIf { !allFrames && state.frameLayerMode && edit !is Edit.ScaleImage && edit !is Edit.CropImage }
            change("Applying edit…") { it.append(Edit.FrameOperation(if(allFrames) null else state.selectedFrame,edit,layer)) }
            return
        }
        val paletteEdit=edit is Edit.PaletteColor || edit is Edit.PaletteShift || edit is Edit.CycleChange
        val embeddedPalette=state.media[state.selectedLayer]?.editablePalette!=null
        val actual=if(state.layered && (edit is Edit.Turn || edit is Edit.Flip || edit is Edit.Crop || edit is Edit.Resize || edit is Edit.Adjust || edit is Edit.PixelCrop || edit is Edit.ClearPixels || (paletteEdit && embeddedPalette))) Edit.OnLayer(state.selectedLayer,edit) else edit
        val scoped=if(state.frameMode && state.frameLayerMode && edit !is Edit.FrameDelay && edit !is Edit.FrameLoops && edit !is Edit.ScaleImage && edit !is Edit.CropImage) Edit.FrameEdit(state.selectedFrame,actual) else actual
        change("Applying edit…") { it.append(scoped) }
    }
    fun undo()=change("Undoing…") { it.undo() }
    fun redo()=change("Redoing…") { it.redo() }
    fun reset()=change("Restoring original document…") { EditHistory() }
    private fun change(message:String,history:(EditHistory)->EditHistory)=task(message) {
        val old=document ?: error("Open an image first")
        val next=old.copy(history=history(old.history)); val result=render(next)
        persist(next); document=next; rendered=result
        publish(next,result,"${result.document.width} × ${result.document.height} · ${next.history.cursor} edits · Draft saved")
    }
    fun selectLayer(id:String) {
        if(state.busy || state.layers.none { it.id==id }) return
        pause();animationOffset=0;playbackFinished=false
        if(id!=state.selectedLayer) clearSelection()
        if(state.frameMode && !state.frameLayerMode) { state=state.copy(selectedFrame=id) }
        val result=rendered
        val bitmap=if(state.frameMode && result!=null) frameBitmap(result,if(state.frameLayerMode) state.selectedFrame else id) else state.bitmap
        val palette=state.media[id]?.editablePalette
        state=state.copy(selectedLayer=id,bitmap=bitmap,playbackFrame=null,colors=palette?.colors ?: result?.colors.orEmpty(),editableColors=palette?.editableColors ?: document?.palette?.editableColors ?: 0,
            cycles=palette?.cycles ?: result?.cycles.orEmpty(),paletteFormat=palette?.format ?: document?.palette?.format); listener?.invoke()
    }
    fun showFrameLayers(enabled:Boolean) {
        if(state.busy || rendered?.project==null) return
        pause();state=state.copy(frameLayerMode=enabled)
        document?.let { publish(it,rendered!!,if(enabled) "Editing layers in the selected animation frame" else "Animation timeline") }
    }
    fun selectionImage():Bitmap?=if(state.frameMode && !state.frameLayerMode) state.bitmap else if(state.layered) state.layers.firstOrNull { it.id==state.selectedLayer }?.bitmap else rendered?.bitmap
    fun clearSelection() { state=state.copy(selection=null,selectionOverlay=null,selectionBounds=null,selectedPixels=0);listener?.invoke() }
    fun selectShape(kind:String,points:List<android.graphics.PointF>,mode:String) {
        val image=selectionImage() ?: return
        select(mode) { when(kind) {
            "lasso" -> PixelSelections.polygon(image.width,image.height,points)
            else -> {
                val a=points.first();val b=points.last()
                val x=kotlin.math.floor(minOf(a.x,b.x)).toInt().coerceIn(0,image.width-1)
                val y=kotlin.math.floor(minOf(a.y,b.y)).toInt().coerceIn(0,image.height-1)
                val r=kotlin.math.ceil(maxOf(a.x,b.x)).toInt().coerceIn(x+1,image.width)
                val bottom=kotlin.math.ceil(maxOf(a.y,b.y)).toInt().coerceIn(y+1,image.height)
                PixelSelections.rectangle(image.width,image.height,x,y,r-x,bottom-y,kind=="ellipse")
            }
        } }
    }
    fun selectPixels(x:Int,y:Int,width:Int,height:Int,ellipse:Boolean,mode:String) {
        val image=selectionImage() ?: return
        select(mode) { PixelSelections.rectangle(image.width,image.height,x,y,width,height,ellipse) }
    }
    fun selectColor(x:Int,y:Int,tolerance:Int,contiguous:Boolean,mode:String) {
        val image=selectionImage() ?: return
        select(mode) { PixelSelections.color(image,x.coerceIn(0,image.width-1),y.coerceIn(0,image.height-1),tolerance,contiguous) }
    }
    fun selectBackground(tolerance:Int,mode:String) {
        val image=selectionImage() ?: return
        select(mode) { PixelSelections.background(image,tolerance) }
    }
    fun selectAll() { val image=selectionImage() ?: return;select("replace") { PixelSelections.rectangle(image.width,image.height,0,0,image.width,image.height) } }
    fun invertSelection() { val old=state.selection ?: return;select("replace") { PixelSelections.invert(old) } }
    private fun select(mode:String,create:()->PixelSelection) {
        val old=state.selection
        task("Selecting pixels…") {
            val next=PixelSelections.combine(old,create(),mode)
            val bounds=PixelSelections.bounds(next);val count=next.coverage.count { it.toInt()!=0 }
            val overlay=Bitmap.createBitmap(next.width,next.height,Bitmap.Config.ALPHA_8)
            val buffer=java.nio.ByteBuffer.allocate(overlay.rowBytes*overlay.height)
            for(y in 0 until next.height) { buffer.position(y*overlay.rowBytes);buffer.put(next.coverage,y*next.width,next.width) }
            buffer.rewind();overlay.copyPixelsFromBuffer(buffer)
            post { state=state.copy(selection=next,selectionOverlay=overlay,selectionBounds=bounds,selectedPixels=count,
                message=if(count==0) "No pixels selected. Try a different point or tolerance." else "$count pixels selected. Review the highlight before clearing.") }
        }
    }
    @JvmOverloads fun clearSelectedPixels(allFrames:Boolean=true) {
        val selection=state.selection ?: return
        if(state.selectedPixels==0) return
        val id=state.selectedLayer;val onLayer=state.layered
        task("Clearing selected pixels…") {
            val old=document ?: error("Open an image first")
            val mask=if(allFrames && state.frameMode && state.frameLayerMode) {
                val layer=state.layers.first { it.id==state.selectedLayer };val coverage=ByteArray(state.width*state.height)
                for(y in 0 until selection.height) {
                    val destinationY=y.toLong()+layer.y;if(destinationY !in 0L until state.height.toLong()) continue
                    val start=maxOf(0L,-layer.x.toLong());val end=minOf(selection.width.toLong(),state.width.toLong()-layer.x)
                    if(start<end) selection.coverage.copyInto(coverage,(destinationY*state.width+start+layer.x).toInt(),y*selection.width+start.toInt(),y*selection.width+end.toInt())
                }
                PixelSelection(state.width,state.height,coverage)
            } else selection
            val asset=File(folder,"asset-${UUID.randomUUID()}.png")
            try {
                val encoded=PixelSelections.toBitmap(mask)
                try { writePng(encoded,asset) } finally { encoded.recycle() }
                val operation=Edit.ClearPixels(asset.name,mask.width,mask.height)
                val scoped=if(state.frameMode) Edit.FrameOperation(if(allFrames) null else state.selectedFrame,operation,id.takeIf { !allFrames && state.frameLayerMode }) else if(onLayer) Edit.OnLayer(id,operation) else operation
                val next=old.copy(history=old.history.append(scoped))
                val result=render(next);persist(next);document=next;rendered=result
                publish(next,result,"Selected pixels cleared to transparent · Undo is available · Draft saved")
            } catch(error:Throwable) { asset.delete();throw error }
        }
    }
    fun importLayer(content:Uri,authoritative:Uri,name:String)=task("Importing layer…") {
        val old=document ?: error("Open an image first")
        val source=InfiniSelect.requireSelectedSource(app,content,authoritative.toString(),displayName=name)
        val bytes=readSource(source.contentUri)
        val file=File(folder,"asset-${UUID.randomUUID()}.bin")
        try {
            writeBytes(file,bytes)
            val extension=name.substringAfterLast('.',"").lowercase(java.util.Locale.ROOT)
            val operation=Edit.MediaImport("layer-${UUID.randomUUID()}",file.name,name,extension,source.mimeType)
            val next=old.copy(history=old.history.append(if(state.frameLayerMode) Edit.FrameEdit(state.selectedFrame,operation) else operation))
            val result=render(next);persist(next);document=next;rendered=result
            publish(next,result,"Media imported as its own layer. ORA retains the embedded source file.")
        } catch(error:Throwable) { file.delete();throw error }
    }
    fun prepareExport(format:String="png")=task("Preparing export…") {
        val doc=document ?: error("Open an image first"); val result=rendered ?: error("No document")
        require(format=="icanim" || format=="indexed" || ImageSaving.formats.any { it.id==format }) { "No writer is installed for $format" }
        if(format=="indexed") require(result.palettePreservable && result.paletteBytes!=null) { "Raster/layer edits need PNG or ORA; undo them to save indexed data" }
        if(format=="icanim") require(result.project!=null) { "Create or open an animation first" }
        if(format=="apng") require(result.frames!=null) { "Enable a frame sequence first" }
        val still=if(result.frames!=null) frameBitmap(result,state.selectedLayer) else result.bitmap
        val extension=if(format=="indexed") doc.palette!!.format else format
        val file=File(app.cacheDir,"canvas-export-${UUID.randomUUID()}.$extension")
        try {
            FileOutputStream(file).use { output ->
                when {
                    format=="icanim" -> ImageSaving.writeAnimationProject(result.project!!,output)
                    format=="ora" && result.project!=null -> OpenRasterDocuments.write(result.project.frames.first { it.id==state.selectedFrame }.document,output)
                    format=="indexed" -> ImageSaving.writePalette(doc.palette!!,output,result.colors,result.cycles)
                    format in setOf("apng","gif") && result.frames!=null -> ImageSaving.writeAnimation(result.frames,format,output)
                    format=="ora" && result.ora!=null -> ImageSaving.writeOpenRaster(result.ora,output)
                    ImageSaving.format(format)?.layers==true -> ImageSaving.writeDocument(result.document,format,output)
                    else -> ImageSaving.write(still,format,output)
                }
                output.fd.sync()
            }
            val next=doc.copy(pendingFormat=format); persist(next); document=next
            post { state=state.copy(export=file,exportFormat=extension,message="Choose where to save the copy.") }
        } catch(error:Throwable) { file.delete(); throw error }
    }
    fun finishExport(success:Boolean,message:String)=task(message) {
        val doc=document ?: return@task
        val result=rendered ?: return@task
        // A flat preview is not a saved layered/indexed document.
        val complete=doc.pendingFormat=="icanim" || doc.pendingFormat=="indexed" || !state.frameMode && doc.playback==null &&
            (ImageSaving.format(doc.pendingFormat)?.layers==true && result.colors.isEmpty() || doc.pendingFormat in setOf("png","bmp","tga","qoi","ff","pam","tiff","webp") && !result.layered && result.colors.isEmpty())
        val next=if(success && complete) doc.copy(exported=doc.history.active) else doc
        try { persist(next); document=next }
        finally { post { state.export?.delete(); state=state.copy(export=null,dirty=next.exported!=next.history.active,message=message) } }
    }
    fun setMessage(message:String) { state=state.copy(message=message); listener?.invoke() }

    fun play() {
        if(state.busy || !state.canAnimate || playing) return
        val restart=playbackFinished
        playbackFinished=false
        if(restart) animationOffset=0
        if(!restart && state.frameMode && animationOffset==0L) {
            val target=state.playbackFrame ?: state.selectedFrame
            animationOffset=rendered?.project?.frames?.takeWhile { it.id!=target }?.sumOf { it.delayMillis*1_000_000L } ?: 0L
        }
        playing=true; val generation=++animationGeneration; animationStart=System.nanoTime()-animationOffset
        state=state.copy(playing=true,playbackFrame=if(restart) rendered?.project?.frames?.firstOrNull()?.id else state.playbackFrame); listener?.invoke()
        if(state.drawable==null) tick(generation)
    }
    fun pause() {
        if(playing) animationOffset=System.nanoTime()-animationStart
        playing=false; animationGeneration++
        val frame=state.playbackFrame.takeIf { !state.frameLayerMode && state.frameItems.any { frame -> frame.id==it } }
        state=state.copy(playing=false,selectedFrame=frame ?: state.selectedFrame,selectedLayer=frame ?: state.selectedLayer); listener?.invoke()
    }
    fun stop() {
        playing=false; animationGeneration++; animationOffset=0;playbackFinished=false
        val frame=rendered?.project?.frames?.firstOrNull()?.id
        state=state.copy(playing=false,bitmap=rendered?.bitmap ?: state.bitmap,playbackFrame=frame,
            selectedFrame=if(!state.frameLayerMode) frame ?: state.selectedFrame else state.selectedFrame,
            selectedLayer=if(!state.frameLayerMode) frame ?: state.selectedLayer else state.selectedLayer); listener?.invoke()
    }
    private fun tick(generation:Int) {
        if(closed || !playing || generation!=animationGeneration) return
        worker.execute {
            val result=rendered
            val animation=result?.animation ?: document?.playback?.let { it.colorCycling ?: it.animation }
            try {
                if(animation!=null) {
                    val elapsed=(System.nanoTime()-animationStart).coerceAtLeast(0)
                    val sequence=result?.frames
                    val finished=sequence!=null && sequence.plays>0 && elapsed/animation.durationNanos>=sequence.plays
                    // Editable frames are immutable cached bitmaps: don't allocate a full canvas every tick.
                    var frameIndex=-1
                    val bitmap=if(sequence!=null) {
                        val time=if(finished) animation.durationNanos-1 else elapsed%animation.durationNanos
                        var end=0L
                        frameIndex=sequence.frames.indexOfFirst { frame -> end+=frame.delayMillis*1_000_000L;time<end }
                        sequence.frames[frameIndex].bitmap
                    } else {
                        val pixels=IntArray(animation.width*animation.height)
                        do { animation.render(elapsed,pixels,true) } while(animation.needsMoreRendering)
                        Bitmap.createBitmap(pixels,animation.width,animation.height,Bitmap.Config.ARGB_8888)
                    }
                    main.post { if(!closed && playing && generation==animationGeneration) {
                        if(finished) { playing=false;animationOffset=0;playbackFinished=true }
                        val frame=result?.project?.frames?.getOrNull(frameIndex)?.id
                        state=state.copy(bitmap=bitmap,playing=!finished,playbackFrame=frame,
                            selectedFrame=if(finished && !state.frameLayerMode) frame ?: state.selectedFrame else state.selectedFrame,
                            selectedLayer=if(finished && !state.frameLayerMode) frame ?: state.selectedLayer else state.selectedLayer); listener?.invoke()
                        if(!finished) main.postDelayed({ tick(generation) },if(sequence!=null) 16 else 33)
                    } else if(sequence==null) bitmap.recycle() }
                }
            } catch(error:Exception) { main.post { if(!closed && generation==animationGeneration) { pause(); setMessage("Playback paused: ${error.message}") } } }
        }
    }
    private fun persist(doc:Document) {
        val json=JSONObject().put("version",3).put("base",doc.file.name).put("kind",when { doc.project!=null->"project";doc.ora!=null->"ora";doc.animation!=null->"animation";doc.playback!=null && doc.palette==null->"playback";doc.layers!=null->"layers";doc.palette!=null->"palette";else->"raster" })
            .put("importNote",doc.importNote ?: "").put("extension",doc.extension).put("name",doc.name).put("source",doc.source ?: "").put("edits",EditCodec.write(doc.history.edits)).put("cursor",doc.history.cursor)
            .put("exported",doc.exported?.let(EditCodec::write) ?: JSONObject.NULL).put("pendingFormat",doc.pendingFormat).put("selectedLayer",state.selectedLayer).put("selectedFrame",state.selectedFrame).put("frameLayerMode",state.frameLayerMode)
        val output=session.startWrite()
        try { output.write(json.toString().toByteArray()); session.finishWrite(output) }
        catch(error:Throwable) { session.failWrite(output); throw error }
    }
    private fun publish(doc:Document,result:DocumentRenderer.Result,message:String)=post {
        val added=if(doc.history.cursor==state.history.cursor+1) when(val edit=doc.history.active.lastOrNull()) {
            is Edit.FrameEdit -> when(val operation=edit.operation) { is Edit.LayerNew -> operation.id;is Edit.MediaImport -> operation.id;is Edit.LayerDuplicate -> operation.newId;else -> null };is Edit.LayerNew -> edit.id; is Edit.LayerImport -> edit.id;is Edit.MediaImport -> edit.id; is Edit.LayerDuplicate -> edit.newId; else -> null
        } else null
        val frameId=listOfNotNull(added,state.selectedFrame).firstOrNull { id -> result.project?.frames?.any { it.id==id }==true } ?: result.project?.frames?.firstOrNull()?.id ?: state.selectedFrame
        val frameDocument=result.project?.frames?.firstOrNull { it.id==frameId }?.document
        val frameLayerMode=state.frameLayerMode && frameDocument!=null
        val currentDocument=if(frameLayerMode) frameDocument!!.document else result.document
        val selected=(if(result.project!=null && !frameLayerMode) frameId else added ?: state.selectedLayer).takeIf { id -> currentDocument.layers.any { it.id==id } } ?: currentDocument.layers.first().id
        val media=if(frameLayerMode) frameDocument!!.assets else result.ora?.assets.orEmpty()
        val playback=doc.playback.takeIf { doc.history.active.isEmpty() && doc.palette==null }
        val activePalette=media[selected]?.editablePalette
        val selectedBitmap=if(result.frames!=null) frameBitmap(result,frameId) else result.bitmap
        animationOffset=0;playbackFinished=false
        state=state.copy(playbackFrame=null,loops=result.project?.plays ?: 0,frameItems=if(result.project!=null) result.document.layers else emptyList(),selectedFrame=frameId,frameLayerMode=frameLayerMode,media=media,sourceFormat=doc.extension,importNote=doc.importNote,selection=null,selectionOverlay=null,selectionBounds=null,selectedPixels=0,bitmap=selectedBitmap,name=doc.name,history=doc.history,dirty=doc.exported!=doc.history.active,
            message=message,width=result.document.width,height=result.document.height,layers=currentDocument.layers,layered=result.layered,selectedLayer=selected,colors=activePalette?.colors ?: result.colors,
            editableColors=activePalette?.editableColors ?: doc.palette?.editableColors ?: 0,cycles=activePalette?.cycles ?: result.cycles,paletteFormat=activePalette?.format ?: doc.palette?.format,
            palettePreservable=result.palettePreservable,canAnimate=result.animation!=null || result.drawable!=null || playback!=null,playing=false,drawable=result.drawable ?: playback?.animationDrawable,frameMode=result.frames!=null,delays=result.delays)
    }
    private fun post(action:()->Unit) { main.post { if(!closed) { action(); listener?.invoke() } } }
    private fun task(message:String,reportOpenFailure:Boolean=false,action:()->Unit) {
        if(state.busy || closed) return
        playing=false; animationGeneration++; animationOffset=0;playbackFinished=false
        state=state.copy(busy=true,playing=false,bitmap=state.bitmap,message=message,openFailure=if(reportOpenFailure) null else state.openFailure); listener?.invoke()
        worker.execute {
            try { action() }
            catch(error:Exception) { post {
                val detail=if(error is SecurityException) "Read access was denied. Open the file from Files again and grant access." else error.message ?: "The file is unavailable or unsupported."
                state=state.copy(message="Could not complete: $detail",openFailure=if(reportOpenFailure) detail else state.openFailure)
            } }
            catch(_:OutOfMemoryError) { post {
                val detail="This operation needs more memory than is available. The previous draft is kept."
                state=state.copy(message=detail,openFailure=if(reportOpenFailure) detail else state.openFailure)
            } }
            finally { post { state=state.copy(busy=false) } }
        }
    }
    private fun sourceName(content:Uri,authoritative:Uri,fallback:String?):String {
        val supplied=runCatching { app.contentResolver.query(content,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0).takeIf { it.isNotBlank() } else null
        } }.getOrNull()
        return supplied ?: fallback ?: InfiniSelect.displayName(app,authoritative) ?: "Image"
    }
    private fun readSource(uri:Uri):ByteArray=app.contentResolver.openInputStream(uri)?.use { input ->
        val out=ByteArrayOutputStream(); val buffer=ByteArray(65536)
        while(true) { if(Thread.currentThread().isInterrupted) throw InterruptedIOException();val n=input.read(buffer);if(n<0) break;require(n>0) { "Image stream made no progress" };out.write(buffer,0,n) }
        out.toByteArray()
    } ?: error("No readable source")
    private fun frameBitmap(result:DocumentRenderer.Result,id:String):Bitmap {
        return result.framePreviews[id] ?: result.bitmap
    }
    private fun animationLayers(animation:AnimationDocument)=LayerDocument(animation.width,animation.height,
        animation.frames.mapIndexed { i,frame -> ImageLayer("frame-$i","Frame ${i+1}",frame.bitmap) })
    private fun decodeFrame(bytes:ByteArray,ext:String)=ThumbnailDecoder(null) { bytes.inputStream() }
        .decodeEditorFrame(Uri.parse("infinicanvas://draft/image.$ext"),ext) ?: error("Animation cannot be decoded")
    private fun decodeBytes(bytes:ByteArray,ext:String):Bitmap {
        val uri=Uri.parse("infinicanvas://draft/image.$ext")
        return ThumbnailDecoder(null) { bytes.inputStream() }.decodeEditorFrame(uri,ext)?.use { it.takeBitmap() } ?: error("Indexed draft cannot be decoded")
    }
    private fun writePng(bitmap:Bitmap,file:File)=BitmapFrame(bitmap).use { frame -> ImageEncoding.png(frame).use { writeBytes(file,it.takeBytes()) } }
    private fun writeBytes(file:File,bytes:ByteArray) { FileOutputStream(file).use { it.write(bytes); it.fd.sync() } }
    fun close() { listener=null; playing=false; animationGeneration++; closed=true; worker.shutdown() }
}
