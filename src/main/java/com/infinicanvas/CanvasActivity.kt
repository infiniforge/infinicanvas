package com.infinicanvas

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import io.github.gretchycat.infiniselect.FileSelectionContract
import io.github.gretchycat.infiniselect.InfiniSelect
import io.github.gretchycat.infiniselect.OpenRequest
import io.github.gretchycat.infiniselect.SaveRequest
import io.github.gretchycat.infiniselect.StorageAccess
import java.io.File

/** Standalone native editor; all document work lives in its retained controller. */
class CanvasActivity : Activity() {
    private lateinit var editor: EditorController
    private lateinit var preview: CanvasPreview
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var toolPanel: LinearLayout
    private lateinit var panelScroll: ScrollView
    private lateinit var toolFooter: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var exportButton:EditorIcon
    private lateinit var undoButton:EditorIcon
    private lateinit var redoButton:EditorIcon
    private lateinit var openButton:EditorIcon
    private lateinit var moreButton:EditorIcon
    private lateinit var transport:LinearLayout
    private lateinit var cropActions:LinearLayout
    private lateinit var playback:EditorIcon
    private val documentControls=mutableListOf<View>()
    private val controlAvailability=mutableMapOf<View,Boolean>()
    private var applyAllFrames=true
    private val frameScopeControls=mutableSetOf<CheckBox>()
    private var framePlayback:EditorIcon?=null
    private var frameHeading:TextView?=null
    private val frameTiles=mutableMapOf<String,View>()
    private val frameEditControls=mutableListOf<View>()
    private var highlightedFrame:String?=null
    private var timelineWasPlaying=false
    private var cyclePlayback:EditorIcon?=null
    private var cycleRestart:EditorIcon?=null
    private val tabs=mutableMapOf<String,EditorIcon>()
    private var toolDialog:AlertDialog?=null
    private var dialogTool:String?=null
    private var dialogHeading:TextView?=null
    private var lastDocumentName=""
    private var selectedPalette=-1
    private var reopenPanel=true
    private var cropLayerOnly=true
    private var selectionTool="rect"
    private var selectionMode="replace"
    private var selectionTolerance=24
    private var selectionClear:EditorIcon?=null
    private var selectionInvert:EditorIcon?=null
    private var cropCaption:TextView?=null
    private var lastStatus=""
    private var lastStatusBusy=false
    private val hideStatus=Runnable { if(::status.isInitialized) status.visibility=View.GONE }
    private var tool = "view"
    private var adjustment = Edit.Adjust()
    private var crop = Edit.Crop(.1f,.1f,.9f,.9f)
    private var pickerPending = false
    private var savePending = false
    private var exportFile: File? = null
    private var deferredIncoming:IncomingImage?=null
    private var incomingScheduled=false
    private var launchHandled=false
    private var handledLaunch:String?=null
    private var incomingConfirmation:AlertDialog?=null
    private var openErrorDialog:AlertDialog?=null
    private var openNoticeDialog:AlertDialog?=null
    private var pendingResult: Triple<Int, Int, Intent?>? = null
    private var toolsHistory: EditHistory? = null
    private var toolsLayer: String? = null
    private var timelineScroll:HorizontalScrollView?=null
    private var timelineScrollX=0
    private var lastBitmap: android.graphics.Bitmap? = null
    private val preferences by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        editor = (lastNonConfigurationInstance as? EditorController) ?: EditorController(this)
        tool = savedInstanceState?.getString("tool") ?: "view"
        cropLayerOnly=savedInstanceState?.getBoolean("cropLayerOnly",true) ?: true
        selectionTool=savedInstanceState?.getString("selectionTool") ?: "rect"
        selectionMode=savedInstanceState?.getString("selectionMode") ?: "replace"
        selectionTolerance=savedInstanceState?.getInt("selectionTolerance",24) ?: 24
        selectedPalette=savedInstanceState?.getInt("paletteIndex",-1) ?: -1
        reopenPanel=savedInstanceState?.getBoolean("panelOpen",true) ?: true
        applyAllFrames=savedInstanceState?.getBoolean("applyAllFrames",true) ?: true
        timelineScrollX=savedInstanceState?.getInt("timelineScrollX",0)?.coerceAtLeast(0) ?: 0
        adjustment = Edit.Adjust(savedInstanceState?.getInt("brightness") ?: 0,
            savedInstanceState?.getInt("contrast") ?: 0, savedInstanceState?.getInt("saturation") ?: 0)
        savedInstanceState?.getFloatArray("crop")?.let { if (it.size==4) crop=Edit.Crop(it[0],it[1],it[2],it[3]) }
        pickerPending = savedInstanceState?.getBoolean("picker") ?: false
        savePending = savedInstanceState?.getBoolean("saving") ?: false
        exportFile = savedInstanceState?.getString("exportFile")?.let(::File)
        launchHandled=savedInstanceState?.getBoolean("launchHandled",false) ?: false
        handledLaunch=savedInstanceState?.getString("handledLaunch")
        deferredIncoming=savedInstanceState?.getBundle("incomingImage")?.let(IncomingImage::restore)
            ?: savedInstanceState?.getString("incoming")?.let { IncomingImage(Uri.parse(it),null,null,null) }
        savedInstanceState?.takeIf { it.containsKey("resultRequest") }?.let {
            @Suppress("DEPRECATION")
            pendingResult=Triple(it.getInt("resultRequest"),it.getInt("resultCode"),it.getParcelable("resultData"))
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        buildUi()
        editor.listener = ::update
        update()
        val launchKey=if(IncomingImage.accepts(intent)) runCatching { IncomingImage.read(intent).key() }.getOrNull() else null
        if(!launchHandled || (launchKey!=null && handledLaunch!=launchKey)) incoming(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRetainNonConfigurationInstance(): Any = editor
    override fun onPause() { editor.pause(); super.onPause() }
    override fun onDestroy() {
        editor.listener = null
        incomingConfirmation?.setOnDismissListener(null);incomingConfirmation?.dismiss()
        openErrorDialog?.dismiss();openNoticeDialog?.dismiss()
        dismissToolDialog()
        if (::status.isInitialized) status.removeCallbacks(hideStatus)
        if (!isChangingConfigurations) editor.close()
        super.onDestroy()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("cropLayerOnly",cropLayerOnly);out.putString("selectionTool",selectionTool)
        out.putString("selectionMode",selectionMode);out.putInt("selectionTolerance",selectionTolerance)
        out.putBoolean("panelOpen",toolDialog!=null);out.putInt("paletteIndex",selectedPalette)
        out.putInt("timelineScrollX",timelineScrollX)
        out.putBoolean("applyAllFrames",applyAllFrames)
        out.putString("tool",tool); out.putInt("brightness",adjustment.brightness)
        out.putInt("contrast",adjustment.contrast); out.putInt("saturation",adjustment.saturation)
        out.putFloatArray("crop",floatArrayOf(preview.crop.left,preview.crop.top,preview.crop.right,preview.crop.bottom))
        out.putBoolean("picker",pickerPending); out.putBoolean("saving",savePending)
        out.putString("exportFile",exportFile?.path); out.putBundle("incomingImage",deferredIncoming?.save());out.putBoolean("launchHandled",launchHandled);out.putString("handledLaunch",handledLaunch)
        pendingResult?.let { (request, code, data) ->
            out.putInt("resultRequest",request); out.putInt("resultCode",code); out.putParcelable("resultData",data)
        }
        super.onSaveInstanceState(out)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incoming(intent) }
    private fun incoming(intent:Intent?) {
        launchHandled=true
        if(!IncomingImage.accepts(intent)) return
        try {
            val request=IncomingImage.read(intent!!)
            handledLaunch=request.key()
            StorageAccess.persist(this,Intent(intent).setData(request.content))
            // A new explicit open supersedes an abandoned open/import picker in this task.
            pickerPending=false
            pendingResult?.takeIf { it.first in setOf(REQUEST_OPEN,REQUEST_LAYER) }?.let { pendingResult=null }
            deferredIncoming=request
            incomingConfirmation?.dismiss();incomingConfirmation=null
            scheduleIncoming()
        } catch(error:Exception) { showOpenError(error.message ?: "The supplied image could not be read.") }
    }
    private fun scheduleIncoming() {
        if(incomingScheduled || deferredIncoming==null || isDestroyed) return
        incomingScheduled=true
        preview.post { incomingScheduled=false;openIncoming() }
    }
    private fun openIncoming() {
        val request=deferredIncoming ?: return
        if(isDestroyed || isFinishing || editor.state.busy || savePending || editor.state.export!=null || incomingConfirmation!=null) return
        fun open() {
            if(deferredIncoming?.id!=request.id) return
            deferredIncoming=null
            activateTool("view");preview.fit()
            editor.open(request)
        }
        if(editor.state.bitmap!=null && (editor.state.dirty || tool=="crop" || !adjustment.isIdentity)) {
            val dialog=AlertDialog.Builder(this).setTitle("Open ${request.name ?: "selected image"}?")
                .setMessage("The current draft has unexported edits. Open the selected image and replace this draft? Your current image is kept if opening fails.")
                .setNegativeButton("Keep editing") { _,_ -> if(deferredIncoming?.id==request.id) deferredIncoming=null }
                .setPositiveButton("Open image") { _,_ -> open() }.create()
            incomingConfirmation=dialog
            dialog.setOnCancelListener { if(deferredIncoming?.id==request.id) deferredIncoming=null }
            dialog.setOnDismissListener { if(incomingConfirmation===dialog) incomingConfirmation=null;scheduleIncoming() }
            dialog.show()
        } else open()
    }
    private fun showOpenError(message:String) {
        if(isDestroyed || isFinishing) return
        editor.setMessage(message)
        openErrorDialog?.dismiss()
        openErrorDialog=AlertDialog.Builder(this).setTitle("Could not open image").setMessage(message)
            .setPositiveButton("OK",null).create().also { it.show() }
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun row() = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 14f, secondary: Boolean = false) = TextView(this).apply {
        text=value; textSize=size; setTextColor(color(if (secondary) R.color.text_secondary else R.color.text_primary))
        setPadding(dp(4),dp(4),dp(4),dp(4))
    }
    private fun background(fill: Int, outline: Boolean = false) = GradientDrawable().apply {
        setColor(color(fill)); cornerRadius=dp(12).toFloat()
        if (outline) setStroke(dp(1),color(R.color.outline))
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        backgroundTintList=null
        text=value; textSize=14f; isAllCaps=false; minHeight=dp(48); minimumWidth=dp(48)
        setPadding(dp(12),dp(4),dp(12),dp(4))
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled),intArrayOf()),
            intArrayOf(color(if (primary) R.color.on_accent else R.color.text_primary),color(R.color.text_secondary))))
        background=RippleDrawable(ColorStateList.valueOf(color(R.color.outline)),
            background(if (primary) R.color.accent else R.color.surface_secondary),null)
        setOnClickListener { action() }
    }
    private fun documentButton(value: String, action: () -> Unit): Button = button(value,action=action).also { documentControls.add(it) }

    private fun icon(symbol:String,label:String,action:()->Unit)=EditorIcon(this,symbol,label).apply { setOnClickListener { action() } }
    private fun LinearLayout.addIcon(view:EditorIcon) { addView(view,LinearLayout.LayoutParams(dp(48),dp(48))) }
    private fun EditorIcon.available(value:Boolean):EditorIcon { controlAvailability[this]=value;return this }
    private fun actionIcon(symbol:String,label:String,action:()->Unit)=icon(symbol,label,action).also { documentControls+=it }
    private fun spacer(parent:LinearLayout) { parent.addView(View(this),LinearLayout.LayoutParams(0,1,1f)) }

    private fun buildUi() {
        val root=column().apply { setBackgroundColor(color(R.color.background)) }
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            };insets
        }
        val heading=row().apply { setBackgroundColor(color(R.color.surface)) }
        openButton=icon("open","Open image") { replaceDraft { dismissToolDialog();openPicker() } };heading.addIcon(openButton)
        val titles=column().apply { setOnClickListener { documentInfo() };isClickable=true;contentDescription="Document information" }
        title=label("InfiniCanvas",14f).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.MIDDLE;typeface=Typeface.DEFAULT_BOLD;setPadding(0,0,0,0) }
        detail=label("Open an image",11f,true).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END;setPadding(0,0,0,0) }
        titles.addView(title);titles.addView(detail);heading.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        undoButton=icon("undo","Undo") { editor.undo() };heading.addIcon(undoButton)
        redoButton=icon("redo","Redo") { editor.redo() };heading.addIcon(redoButton)
        exportButton=icon("export","Export a copy") { exportInfo() };heading.addIcon(exportButton)
        moreButton=icon("more","More options") { overflowMenu() };heading.addIcon(moreButton)
        root.addView(heading,LinearLayout.LayoutParams(-1,dp(maxOf(48,(36*resources.configuration.fontScale).toInt()))))
        val frame=FrameLayout(this)
        preview=CanvasPreview(this).apply { crop=this@CanvasActivity.crop;adjustment=this@CanvasActivity.adjustment;cropping=tool=="crop" }
        frame.addView(preview,FrameLayout.LayoutParams(-1,-1))
        preview.onCropChanged={ updateCropCaption() }
        preview.onSelectionShape={ kind,points ->
            if(kind=="lasso" && points.size<3) editor.setMessage("Drag around an area to make a lasso selection.")
            else editor.selectShape(kind,points,selectionMode)
        }
        preview.onSelectionPoint={ x,y -> editor.selectColor(x,y,selectionTolerance,selectionTool=="wand",selectionMode) }
        preview.onLayerMove={ dx,dy -> editor.state.layers.firstOrNull { it.id==editor.state.selectedLayer }?.let { layer ->
            applyEdit(Edit.LayerProperties(layer.id,layer.name,layer.visible,layer.opacity,(layer.x.toLong()+dx).coerceIn(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()).toInt(),(layer.y.toLong()+dy).coerceIn(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()).toInt(),layer.blend))
        } }
        emptyMessage=label("Open an image to begin",17f,true).apply { gravity=Gravity.CENTER }
        frame.addView(emptyMessage,FrameLayout.LayoutParams(-1,-1))
        progress=ProgressBar(this).apply { isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(color(R.color.accent)) }
        frame.addView(progress,FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER))
        status=label("",12f).apply {
            setPadding(dp(12),dp(8),dp(12),dp(8));maxLines=2;ellipsize=TextUtils.TruncateAt.END
            background=background(R.color.surface,true);accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility=View.GONE;setOnClickListener { documentInfo() }
        }
        frame.addView(status,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { setMargins(dp(12),0,dp(12),dp(8)) })
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        transport=row().apply { setBackgroundColor(color(R.color.surface)) }
        playback=icon("play","Play animation or color cycling") { if(editor.state.playing) editor.pause() else editor.play() }
        playback.setOnLongClickListener { editor.stop();editor.play();true };transport.addIcon(playback)
        val strip=row()
        for((key,caption) in listOf("select" to "Selection tools","resize" to "Scale image…","palette" to "Palette","layers" to "Layers","transform" to "Transform","crop" to "Crop", "adjust" to "Adjust","cycles" to "Color cycling","frames" to "Animation timeline")) {
            val view=icon(key,caption) { if(key=="resize") resizeDialog() else switchTool(key) };tabs[key]=view;strip.addIcon(view)
        }
        strip.addIcon(icon("fit","Fit image") { preview.fit() })
        transport.addView(HorizontalScrollView(this).apply { addView(strip);isHorizontalScrollBarEnabled=false;isFillViewport=true },LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(transport,LinearLayout.LayoutParams(-1,dp(48)))
        cropActions=row().apply { setBackgroundColor(color(R.color.surface));visibility=View.GONE }
        root.addView(cropActions,LinearLayout.LayoutParams(-1,dp(48)))
        setContentView(root);root.requestApplyInsets()
        if(!reopenPanel && tool in setOf("layers","frames")) update() else buildTools()
    }

    private fun documentInfo() {
        val state=editor.state
        AlertDialog.Builder(this).setTitle(if(state.bitmap==null) "InfiniCanvas" else state.name)
            .setMessage(if(state.bitmap==null) "Open an image or create a canvas from More options. Pinch to zoom, drag to pan, double tap to fit. Long press any toolbar icon for its name."
                else "${state.width} × ${state.height} px\n${if(state.dirty) "Draft saved · Unexported changes" else "Draft saved"}\n\n${state.message}${state.importNote?.let { "\n\n$it" }.orEmpty()}")
            .setPositiveButton("Done",null).show()
    }
    private fun overflowMenu() {
        PopupMenu(this,moreButton).apply {
            menu.add(0,1,0,"New canvas")
            menu.add(0,2,1,"Fit image").isEnabled=editor.state.bitmap!=null
            menu.add(0,3,2,"Restart animation").isEnabled=editor.state.canAnimate
            menu.add(0,4,3,"Reset all edits").isEnabled=editor.state.history.canUndo
            menu.add(0,5,4,"Appearance")
            menu.add(0,6,5,"Document information")
            menu.add(0,8,6,"View at 1:1 pixels").isEnabled=editor.state.bitmap!=null
            if(editor.state.bitmap!=null && editor.state.colors.isEmpty()) menu.add(0,7,6,"Palette…")
            setOnMenuItemClickListener { item -> when(item.itemId) {
                1 -> replaceDraft { newCanvasDialog() }
                2 -> preview.fit()
                3 -> { editor.stop();editor.play() }
                4 -> AlertDialog.Builder(this@CanvasActivity).setTitle("Reset all edits?").setMessage("Restore the original document and clear its history.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Reset") { _,_ -> editor.reset() }.show()
                5 -> chooseTheme()
                6 -> documentInfo()
                7 -> switchTool("palette")
                8 -> preview.actualPixels()
            };true };show()
        }
    }
    private fun dismissToolDialog() { val old=toolDialog;toolDialog=null;dialogTool=null;old?.dismiss() }
    private fun ensureToolDialog() {
        if(toolDialog!=null && dialogTool==tool) return
        dismissToolDialog()
        val content=column().apply { setPadding(dp(8),dp(4),dp(8),dp(4)) }
        val heading=row()
        val name=when(tool) { "palette" -> "Palette · ${editor.state.editableColors}";"cycles" -> "Color cycling";"frames" -> "Animation timeline";"layers" -> "Layers";"adjust" -> "Adjust";else -> "Transform" }
        dialogHeading=label(name,16f).apply { typeface=Typeface.DEFAULT_BOLD }
        heading.addView(dialogHeading,LinearLayout.LayoutParams(0,-2,1f))
        heading.addIcon(icon("close",if(tool=="adjust") "Discard adjustment preview" else "Close $name") { toolDialog?.dismiss() })
        content.addView(heading)
        toolPanel=column()
        val maximum=dp((resources.configuration.screenHeightDp*.8f).toInt()-96).coerceAtLeast(dp(96))
        panelScroll=object:ScrollView(this) {
            override fun onMeasure(w:Int,h:Int) {
                val limit=if(View.MeasureSpec.getMode(h)==View.MeasureSpec.UNSPECIFIED) maximum else minOf(maximum,View.MeasureSpec.getSize(h))
                super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST))
            }
        }.apply { addView(toolPanel);isFillViewport=false }
        content.addView(panelScroll,LinearLayout.LayoutParams(-1,-2))
        toolFooter=column();content.addView(toolFooter)
        val mode=tool
        val dialog=AlertDialog.Builder(this).setView(content).create()
        toolDialog=dialog;dialogTool=mode
        dialog.setOnDismissListener {
            if(toolDialog===dialog) {
                toolDialog=null;dialogTool=null
                if(mode !in setOf("layers","frames")) {
                    tool="view";adjustment=Edit.Adjust();preview.adjustment=adjustment
                    if(mode=="adjust") lastBitmap=null
                }
                update()
            }
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val width=if(mode=="palette") minOf(resources.configuration.screenWidthDp-24,960) else minOf(resources.configuration.screenWidthDp-24,440)
        dialog.window?.apply {
            setLayout(dp(width),ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            attributes=attributes.apply { dimAmount=if(mode=="adjust") 0f else .18f }
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    private fun applyEdit(edit:Edit)=editor.edit(edit,applyAllFrames)
    private fun frameScope(parent:ViewGroup,onChange:(()->Unit)?=null) {
        if(!editor.state.frameMode) return
        parent.addView(CheckBox(this).apply {
            text="Apply to all frames";isChecked=applyAllFrames;minHeight=dp(48)
            contentDescription="Apply this image operation to all animation frames"
            frameScopeControls+=this
            addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view:View) {}
                override fun onViewDetachedFromWindow(view:View) { frameScopeControls.remove(this@apply) }
            })
            setOnCheckedChangeListener { _,checked ->
                if(applyAllFrames!=checked) {
                    applyAllFrames=checked
                    frameScopeControls.filter { it!==this }.forEach { it.isChecked=checked }
                }
                onChange?.invoke();updateCropCaption()
            }
            documentControls+=this
        })
    }
    private fun followPlaybackFrame(id:String) {
        if(dialogTool!="frames" || toolDialog?.isShowing!=true) return
        val timeline=timelineScroll ?: return
        val tile=frameTiles[id] ?: return
        val content=timeline.getChildAt(0) ?: return
        if(timeline.width<=0 || tile.width<=0) return
        val inset=dp(6)
        val left=content.left+tile.left-inset
        val right=content.left+tile.right+inset
        val visibleLeft=timeline.scrollX+timeline.paddingLeft
        val visibleRight=timeline.scrollX+timeline.width-timeline.paddingRight
        val visibleWidth=visibleRight-visibleLeft
        val target=(when {
            right-left>visibleWidth -> left-timeline.paddingLeft
            left<visibleLeft -> left-timeline.paddingLeft
            right>visibleRight -> right-timeline.width+timeline.paddingRight
            else -> timeline.scrollX
        }).coerceIn(0,maxOf(0,content.width-visibleWidth))
        // Move only enough to reveal the active tile; instant scrolling keeps up with short frames and loop wraps.
        if(target!=timeline.scrollX) timeline.scrollTo(target,0)
    }
    private fun updateFrameHighlight() {
        val state=editor.state;val current=state.playbackFrame ?: state.selectedFrame
        val follow=state.playing || timelineWasPlaying
        timelineWasPlaying=state.playing
        if(follow) followPlaybackFrame(current)
        if(highlightedFrame==current) return
        highlightedFrame?.let { frameTiles[it]?.let { tile -> tile.isSelected=false;(tile.background as? GradientDrawable)?.setStroke(dp(1),color(R.color.outline)) } }
        frameTiles[current]?.let { tile -> tile.isSelected=true;(tile.background as? GradientDrawable)?.setStroke(dp(2),color(R.color.accent)) }
        highlightedFrame=current
        val index=state.frameItems.indexOfFirst { it.id==current }
        frameHeading?.text=if(index>=0) "Frame ${index+1} / ${state.frameItems.size} · Select to edit" else "${state.frameItems.size} frames"
    }
    private fun buildTools() {
        documentControls.clear();controlAvailability.clear();cyclePlayback=null;cycleRestart=null
        framePlayback=null;frameHeading=null;frameTiles.clear();frameEditControls.clear();highlightedFrame=null
        cropActions.removeAllViews()
        selectionClear=null;selectionInvert=null;cropCaption=null
        if(tool=="crop") {
            dismissToolDialog()
            cropActions.addIcon(actionIcon("close","Cancel crop") { activateTool("view") })
            cropActions.addIcon(actionIcon("aspect","Crop aspect ratio") { anchorCropPresets() })
            cropActions.addIcon(actionIcon("index","Exact pixel crop…") { pixelBoundsDialog(false) })
            cropActions.addIcon(actionIcon("pixel","View at 1:1 pixels") { preview.actualPixels() })
            cropCaption=label("",11f,true).apply { maxLines=2;gravity=Gravity.CENTER }
            cropActions.addView(cropCaption,LinearLayout.LayoutParams(0,-2,1f))
            cropActions.addIcon(actionIcon("check","Apply crop") { applyPixelCrop(preview.cropPixelBounds()) }.apply { isSelected=true })
        } else if(tool=="select") {
            dismissToolDialog()
            cropActions.addIcon(actionIcon("close","Close selection tools") { activateTool("view") })
            cropActions.addIcon(actionIcon(selectionTool,"Choose selection tool") { selectionToolMenu() })
            cropActions.addIcon(actionIcon("properties","Selection settings and background detection…") { selectionOptionsDialog() })
            cropActions.addIcon(actionIcon("index","Exact pixel selection…") { pixelBoundsDialog(true) })
            selectionInvert=actionIcon("invert","Invert selection") { editor.invertSelection() };cropActions.addIcon(selectionInvert!!)
            spacer(cropActions)
            selectionClear=actionIcon("clear","Clear selection to transparent") { editor.clearSelectedPixels(applyAllFrames) };cropActions.addIcon(selectionClear!!)
        } else if(tool!="view") {
            ensureToolDialog()
            if(tool=="palette") dialogHeading?.text="Palette · ${editor.state.editableColors}"
            val scroll=panelScroll.scrollY
            toolPanel.removeAllViews();toolFooter.removeAllViews()
            if(tool in setOf("palette","cycles","transform","adjust")) frameScope(toolPanel)
            when(tool) {
                "palette" -> buildPaletteTools()
                "layers" -> buildLayerTools()
                "frames" -> { if(editor.state.frameLayerMode) editor.showFrameLayers(false);buildFrameTools() }
                "cycles" -> buildCycleTools()
                "transform" -> {
                    val actions=row()
                    actions.addIcon(actionIcon("rotateLeft","Rotate left") { applyEdit(Edit.Turn(false)) })
                    actions.addIcon(actionIcon("rotateRight","Rotate right") { applyEdit(Edit.Turn()) })
                    actions.addIcon(actionIcon("flipH","Flip horizontally") { applyEdit(Edit.Flip()) })
                    actions.addIcon(actionIcon("flipV","Flip vertically") { applyEdit(Edit.Flip(false)) })
                    actions.addIcon(actionIcon("resize","Scale image…") { resizeDialog() })
                    actions.addIcon(actionIcon("crop","Crop on canvas") { activateTool("crop") })
                    toolPanel.addView(HorizontalScrollView(this).apply { addView(actions);isHorizontalScrollBarEnabled=false })
                }
                "adjust" -> {
                    slider("Brightness",adjustment.brightness) { adjustment=adjustment.copy(brightness=it);preview.adjustment=adjustment }
                    slider("Contrast",adjustment.contrast) { adjustment=adjustment.copy(contrast=it);preview.adjustment=adjustment }
                    slider("Saturation",adjustment.saturation) { adjustment=adjustment.copy(saturation=it);preview.adjustment=adjustment }
                    applyCancel { val edit=adjustment;activateTool("view");if(!edit.isIdentity) applyEdit(edit) }
                }
            }
            val currentScroll=panelScroll
            currentScroll.post { currentScroll.scrollTo(0,scroll) }
        } else dismissToolDialog()
        toolsHistory=editor.state.history;toolsLayer=editor.state.selectedLayer
        update()
    }
    private fun buildPaletteTools() {
        val state=editor.state
        if(state.colors.isEmpty()) {
            toolPanel.addView(label("Open an indexed PNG or ILBM/PBM IFF to edit its color table. Reopen the source if this is an older raster-only draft.",13f,true));return
        }
        val width=minOf(resources.configuration.screenWidthDp-24,960)
        val columns=if(width>=640 || (width>=480 && resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)) 32 else 16
        val grid=PaletteGrid(this,state.colors.take(state.editableColors),columns,selectedPalette) { paletteColorDialog(it) }
        for(i in 0 until grid.childCount) documentControls+=grid.getChildAt(i)
        toolPanel.addView(grid,LinearLayout.LayoutParams(-1,-2))
        val actions=row()
        actions.addIcon(actionIcon("cycles","Rotate a palette range…") { paletteRotationDialog() })
        actions.addIcon(actionIcon("index","Choose palette index…") { paletteIndexDialog() })
        spacer(actions)
        actions.addIcon(icon("info","About indexed colors") {
            AlertDialog.Builder(this).setTitle("Indexed palette").setMessage("Tap an index to pick its color. Duplicate colors remain separate entries. Rotation moves colors without rewriting pixel indices. PNG supports palette opacity; IFF keeps its original mask or transparent index.").setPositiveButton("Done",null).show()
        });toolFooter.addView(actions)
    }
    private fun paletteIndexDialog() {
        val field=dimensionInput("Palette index",0)
        val body=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8));addView(label("Index 0–${editor.state.editableColors-1}"));addView(field) }
        val dialog=AlertDialog.Builder(this).setTitle("Choose a color").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Edit",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val index=field.text.toString().toIntOrNull()
            if(index==null || index !in 0 until editor.state.editableColors) field.error="Choose a valid palette index"
            else { dialog.dismiss();paletteColorDialog(index) }
        } };dialog.show()
    }
    private fun paletteColorDialog(index:Int) {
        selectedPalette=index
        if(::toolPanel.isInitialized && tool=="palette") (toolPanel.getChildAt(0) as? PaletteGrid)?.selectIndex(index)
        val state=editor.state
        val original=state.colors[index]
        val hasAlpha=state.paletteFormat=="png"
        var picked=original
        var changing=false
        val content=column().apply { setPadding(dp(16),0,dp(16),0) }
        val samples=row()
        samples.addView(EditorThumbnail(this,swatch=original).apply { contentDescription="Original color" },LinearLayout.LayoutParams(0,dp(28),1f))
        val sample=EditorThumbnail(this,swatch=original).apply { contentDescription="New color" }
        samples.addView(sample,LinearLayout.LayoutParams(0,dp(28),1f));content.addView(samples)
        frameScope(content)
        val picker=HsvColorPicker(this,original)
        content.addView(picker,LinearLayout.LayoutParams(-1,dp(200)))
        val hex=EditText(this).apply {
            hint=if(hasAlpha) "AARRGGBB" else "RRGGBB";contentDescription="Exact hex color";minHeight=dp(48)
            maxLines=1;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val valueRow=row()
        valueRow.addView(label("#",16f));valueRow.addView(hex,LinearLayout.LayoutParams(0,dp(48),1f));content.addView(valueRow)
        val alpha=SeekBar(this).apply { max=255;progress=original ushr 24;minHeight=dp(48);contentDescription="Color opacity" }
        if(hasAlpha) {
            val alphaRow=row();alphaRow.addView(label("Opacity",12f,true));alphaRow.addView(alpha,LinearLayout.LayoutParams(0,dp(48),1f));content.addView(alphaRow)
        }
        fun show(value:Int,writeHex:Boolean,updatePicker:Boolean=true) {
            picked=value;sample.swatchColor=value;if(updatePicker) picker.color=value
            if(writeHex) { changing=true;hex.setText(String.format(if(hasAlpha) "%08X" else "%06X",if(hasAlpha) value else value and 0xffffff));changing=false }
        }
        picker.onColorChanged={ rgb -> show((picked and 0xff000000.toInt()) or (rgb and 0xffffff),true,false) }
        alpha.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view:SeekBar?,value:Int,fromUser:Boolean) { if(fromUser) show((value shl 24) or (picked and 0xffffff),true,false) }
            override fun onStartTrackingTouch(view:SeekBar?) {}
            override fun onStopTrackingTouch(view:SeekBar?) {}
        })
        fun parse():Int? {
            val raw=hex.text.toString().trim().removePrefix("#")
            if((raw.length!=6 && !(hasAlpha && raw.length==8)) || raw.any { it.digitToIntOrNull(16)==null }) return null
            return raw.toLongOrNull(16)?.let { if(raw.length==6) it.toInt() or 0xff000000.toInt() else it.toInt() }
        }
        show(original,true)
        hex.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) { if(!changing) parse()?.let { show(it,false);alpha.progress=it ushr 24 } }
        })
        val dialog=AlertDialog.Builder(this).setTitle("Color $index").setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val argb=parse()
            if(argb==null) hex.error="Enter six RGB digits${if(hasAlpha) " or eight ARGB digits" else ""}"
            else { dialog.dismiss();applyEdit(Edit.PaletteColor(index,argb)) }
        } };dialog.show()
    }
    private fun paletteRotationDialog() {
        val fields=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        frameScope(fields)
        fields.addView(label("Rotate palette values by one slot. Pixel indices stay unchanged.",13f,true))
        val first=dimensionInput("First index",0); val last=dimensionInput("Last index",editor.state.editableColors-1)
        fields.addView(label("First index")); fields.addView(first); fields.addView(label("Last index")); fields.addView(last)
        val dialog=AlertDialog.Builder(this).setTitle("Rotate palette colors").setView(fields)
            .setNegativeButton("Cancel",null).setNeutralButton("Left",null).setPositiveButton("Right",null).create()
        dialog.setOnShowListener {
            fun apply(steps:Int) {
                val lo=first.text.toString().toIntOrNull(); val hi=last.text.toString().toIntOrNull()
                if(lo==null || lo !in 0 until editor.state.editableColors) first.error="Invalid index"
                else if(hi==null || hi !in lo until editor.state.editableColors) last.error="Invalid index"
                else { dialog.dismiss(); applyEdit(Edit.PaletteShift(lo,hi,steps)) }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { apply(-1) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { apply(1) }
        }; dialog.show()
    }
    private fun buildCycleTools() {
        val state=editor.state
        if(state.paletteFormat!="iff") { toolPanel.addView(label("Open an indexed IFF to edit cycling ranges.",13f,true));return }
        if(state.cycles.isEmpty()) toolPanel.addView(label("Add a range to cycle palette colors.",13f,true))
        state.cycles.forEachIndexed { index,cycle ->
            toolPanel.addView(documentButton("${if(cycle.enabled) "●" else "○"} ${cycle.first}–${cycle.last} · ${cycle.millihertz/1000.0}/s ${if(cycle.reverse) "←" else "→"}") { cycleDialog(index) })
        }
        val actions=row()
        actions.addIcon(actionIcon("new","Add cycling range") { cycleDialog(state.cycles.size) }.available(state.cycles.size<256))
        spacer(actions)
        cyclePlayback=actionIcon(if(state.playing) "pause" else "play","Play cycling") { if(editor.state.playing) editor.pause() else editor.play() }
        cycleRestart=actionIcon("restart","Restart cycling") { editor.stop();editor.play() }
        actions.addIcon(cyclePlayback!!);actions.addIcon(cycleRestart!!)
        toolFooter.addView(actions)
    }
    private fun cycleDialog(index:Int) {
        val old=editor.state.cycles.getOrNull(index)
        val fields=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        val first=dimensionInput("First palette index",old?.first ?: 0)
        val last=dimensionInput("Last palette index",old?.last ?: minOf(7,editor.state.editableColors-1))
        val speed=EditText(this).apply { minHeight=dp(48); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(((old?.millihertz ?: 6000)/1000.0).toString()); contentDescription="Palette steps per second" }
        fields.addView(label("First index")); fields.addView(first); fields.addView(label("Last index")); fields.addView(last)
        fields.addView(label("Palette steps per second (0.001–239)")); fields.addView(speed)
        val enabled=CheckBox(this).apply { text="Enabled"; isChecked=old?.enabled ?: true; minHeight=dp(48) }
        val reverse=CheckBox(this).apply { text="Reverse direction"; isChecked=old?.reverse ?: false; minHeight=dp(48) }
        fields.addView(enabled); fields.addView(reverse);frameScope(fields)
        val dialog=AlertDialog.Builder(this).setTitle("Cycling range ${index+1}").setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val lo=first.text.toString().toIntOrNull(); val hi=last.text.toString().toIntOrNull(); val hz=speed.text.toString().toDoubleOrNull()
            if(lo==null || lo !in 0 until editor.state.editableColors) first.error="Invalid index"
            else if(hi==null || hi<=lo || hi>=editor.state.editableColors) last.error="Choose an index above First"
            else if(hz==null || !hz.isFinite() || hz !in .001..239.0) speed.error="Enter 0.001–239"
            else { dialog.dismiss(); applyEdit(Edit.CycleChange(index,lo,hi,(hz*1000).toInt().coerceAtLeast(1),reverse.isChecked,enabled.isChecked)) }
        } }; dialog.show()
    }
    private fun buildLayerTools() {
        val state=editor.state
        for(layer in state.layers) {
            val selected=layer.id==state.selectedLayer
            val line=row().apply {
                setPadding(dp(2),dp(2),dp(2),dp(2))
                background=background(if(selected) R.color.surface_secondary else R.color.surface).apply { if(selected) setStroke(dp(1),color(R.color.accent)) }
            }
            line.addView(CheckBox(this).apply {
                isChecked=layer.visible;minWidth=dp(44);minHeight=dp(48);contentDescription="Show ${layer.name}"
                setOnCheckedChangeListener { _,checked -> applyEdit(Edit.LayerProperties(layer.id,layer.name,checked,layer.opacity,layer.x,layer.y,layer.blend)) };documentControls+=this
            },LinearLayout.LayoutParams(dp(44),dp(48)))
            val pick=row().apply {
                isClickable=true;isFocusable=true;minimumHeight=dp(48)
                contentDescription="${if(selected) "Selected, " else ""}${layer.name}, ${layer.opacity*100/255} percent opacity"
                setOnClickListener { editor.selectLayer(layer.id) }
                setOnLongClickListener { layerPropertiesDialog(layer);true }
            }
            val media=state.media[layer.id]
            pick.addView(if(media?.renderable==false) label(media.extension.uppercase().ifBlank { "FILE" },10f,true).apply { gravity=Gravity.CENTER } else EditorThumbnail(this,layer.bitmap),LinearLayout.LayoutParams(dp(40),dp(40)))
            pick.addView(column().apply {
                addView(label(layer.name,13f).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END;typeface=if(selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT })
                addView(label("${media?.extension?.uppercase()?.let { "$it · " }.orEmpty()}${if(media?.renderable==false) "Retained file" else "${layer.opacity*100/255}% · ${layer.blend}"}",10f,true))
            },LinearLayout.LayoutParams(0,-2,1f))
            documentControls+=pick;line.addView(pick,LinearLayout.LayoutParams(0,-2,1f))
            toolPanel.addView(line)
        }
        val layer=state.layers.firstOrNull { it.id==state.selectedLayer } ?: return
        val position=state.layers.indexOf(layer)
        val actions=row()
        actions.addIcon(actionIcon("new","Add blank layer") { applyEdit(Edit.LayerNew("layer-${java.util.UUID.randomUUID()}","Layer ${editor.state.layers.size+1}")) })
        actions.addIcon(actionIcon("import","Import media as layer") { openLayerPicker() })
        actions.addIcon(actionIcon("properties","Layer properties") { layerPropertiesDialog(layer) })
        actions.addIcon(actionIcon("up","Move layer up") { if(position>0) applyEdit(Edit.LayerMove(layer.id,position-1)) }.available(position>0))
        actions.addIcon(actionIcon("down","Move layer down") { if(position<state.layers.lastIndex) applyEdit(Edit.LayerMove(layer.id,position+1)) }.available(position<state.layers.lastIndex))
        actions.addIcon(actionIcon("duplicate","Duplicate layer") { applyEdit(Edit.LayerDuplicate(layer.id,"layer-${java.util.UUID.randomUUID()}")) })
        actions.addIcon(actionIcon("delete","Delete layer") {
            if(state.layers.size>1) AlertDialog.Builder(this).setTitle("Delete ${layer.name}?").setNegativeButton("Cancel",null)
                .setPositiveButton("Delete") { _,_ -> applyEdit(Edit.LayerDelete(layer.id)) }.show()
            else editor.setMessage("Keep at least one layer.")
        }.available(state.layers.size>1))
        toolFooter.addView(HorizontalScrollView(this).apply { addView(actions);isHorizontalScrollBarEnabled=false })
    }
    private fun buildFrameTools() {
        val state=editor.state
        if(!state.frameMode) {
            toolPanel.addView(label("Open APNG, FLI/FLC or .icanim to edit an animation. Each frame has its own layers.",13f,true))
            if(state.drawable!=null) toolPanel.addView(label("This format currently supports playback only. Creating a sequence here uses its still poster.",13f,true))
            toolPanel.addView(documentButton("Use layers as frames…") {
                AlertDialog.Builder(this).setTitle("Use layers as frames?")
                    .setMessage("Visible layers will play one at a time, top to bottom, initially for 100 ms each. You can undo this conversion.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Create sequence") { _,_ -> applyEdit(Edit.FrameMode(true)) }.show()
            });return
        }
        frameHeading=label("${state.frameItems.size} frames · Select to edit",14f);toolPanel.addView(frameHeading)
        val strip=row()
        state.frameItems.forEachIndexed { index,layer ->
            val tile=column().apply {
                setPadding(dp(4),dp(4),dp(4),dp(4));isClickable=true;isFocusable=true
                background=background(R.color.surface_secondary,true)
                contentDescription="Select frame ${index+1}, ${state.delays[layer.id] ?: 100} milliseconds${if(!layer.visible) ", excluded" else ""}"
                setOnClickListener { editor.selectLayer(layer.id) }
                addView(EditorThumbnail(this@CanvasActivity,layer.bitmap),LinearLayout.LayoutParams(-1,dp(52)))
                addView(label("${index+1} · ${state.delays[layer.id] ?: 100} ms${if(!layer.visible) " · off" else ""}",11f).apply { gravity=Gravity.CENTER })
            }
            documentControls+=tile;frameTiles[layer.id]=tile
            strip.addView(tile,LinearLayout.LayoutParams(dp(88),-2).apply { rightMargin=dp(6) })
        }
        val savedScrollX=timelineScrollX
        val timeline=object:HorizontalScrollView(this) {
            private var restorePosition=true
            override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
                super.onLayout(changed,left,top,right,bottom)
                if(restorePosition) {
                    // Restore after child measurement/focus; scrollTo clamps after frame deletion.
                    scrollTo(savedScrollX,0)
                    restorePosition=false
                    if(timelineScroll===this) {
                        timelineScrollX=scrollX
                        if(editor.state.playing) followPlaybackFrame(editor.state.playbackFrame ?: editor.state.selectedFrame)
                    }
                }
            }
            override fun onScrollChanged(x:Int,y:Int,oldX:Int,oldY:Int) {
                super.onScrollChanged(x,y,oldX,oldY)
                if(!restorePosition && timelineScroll===this) timelineScrollX=x
            }
        }.apply { addView(strip);isHorizontalScrollBarEnabled=true }
        timelineScroll=timeline
        toolPanel.addView(timeline)
        val layer=state.frameItems.firstOrNull { it.id==state.selectedFrame } ?: return
        val position=state.frameItems.indexOf(layer)
        val actions=row()
        actions.addIcon(actionIcon("duration","Frame duration…") { frameDurationDialog(layer.id) })
        actions.addIcon(actionIcon("left","Move frame earlier") { if(position>0) applyEdit(Edit.LayerMove(layer.id,position-1)) }.available(position>0))
        actions.addIcon(actionIcon("right","Move frame later") { if(position<state.frameItems.lastIndex) applyEdit(Edit.LayerMove(layer.id,position+1)) }.available(position<state.frameItems.lastIndex))
        actions.addIcon(actionIcon("duplicate","Duplicate frame") { applyEdit(Edit.LayerDuplicate(layer.id,"layer-${java.util.UUID.randomUUID()}")) })
        actions.addIcon(actionIcon("import","Import image as frame") { openLayerPicker() })
        actions.addIcon(actionIcon("delete","Delete frame") { if(state.frameItems.size>1) applyEdit(Edit.LayerDelete(layer.id)) else editor.setMessage("Keep at least one frame.") }.available(state.frameItems.size>1))
        actions.addIcon(actionIcon("layers","Edit layers in this frame") { editor.showFrameLayers(true);tool="layers";buildTools() })
        actions.addIcon(actionIcon("cycles","Animation loop count…") { loopCountDialog() })
        for(i in 0 until actions.childCount) frameEditControls+=actions.getChildAt(i)
        val footer=row()
        framePlayback=actionIcon("play","Play animation") { if(editor.state.playing) editor.pause() else editor.play() }
        footer.addIcon(framePlayback!!)
        footer.addView(HorizontalScrollView(this).apply { addView(actions);isHorizontalScrollBarEnabled=false },LinearLayout.LayoutParams(0,dp(48),1f))
        toolFooter.addView(footer)
        updateFrameHighlight()
    }
    private fun loopCountDialog() {
        val field=dimensionInput("Loop count; zero repeats forever",editor.state.loops)
        val content=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8));addView(label("0 repeats forever; positive values play that many times."));addView(field) }
        val dialog=AlertDialog.Builder(this).setTitle("Animation loops").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val count=field.text.toString().toIntOrNull()
            if(count==null || count<0) field.error="Enter zero or a positive number" else { dialog.dismiss();applyEdit(Edit.FrameLoops(count)) }
        } };dialog.show()
    }
    private fun frameDurationDialog(id:String) {
        val field=dimensionInput("Frame duration in milliseconds",editor.state.delays[id] ?: 100)
        val content=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8));addView(label("Duration in milliseconds · 1–65535000"));addView(field) }
        val dialog=AlertDialog.Builder(this).setTitle("Frame duration").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value=field.text.toString().toIntOrNull()
            if(value==null || value !in 1..65535000) field.error="Enter 1–65535000 ms"
            else { dialog.dismiss();applyEdit(Edit.FrameDelay(id,value)) }
        } };dialog.show()
    }
    private fun layerPropertiesDialog(layer:com.infinifolder.preview.ImageLayer) {
        val fields=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        val name=EditText(this).apply { setText(layer.name); minHeight=dp(48); contentDescription="Layer name" }
        val x=dimensionInput("Horizontal offset",layer.x).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val y=dimensionInput("Vertical offset",layer.y).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val opacityLabel=label("Opacity ${layer.opacity*100/255}%")
        val opacity=SeekBar(this).apply { max=255; progress=layer.opacity; minHeight=dp(48); contentDescription="Layer opacity" }
        opacity.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(v:SeekBar?,value:Int,fromUser:Boolean) { opacityLabel.text="Opacity ${value*100/255}%" }
            override fun onStartTrackingTouch(v:SeekBar?) {}
            override fun onStopTrackingTouch(v:SeekBar?) {}
        })
        val modes=listOf("normal","multiply","screen","darken","lighten").let { if(layer.blend in it) it else it+layer.blend }
        val blend=Spinner(this).apply { minimumHeight=dp(48); contentDescription="Layer blend mode"; adapter=ArrayAdapter(this@CanvasActivity,android.R.layout.simple_spinner_dropdown_item,modes); setSelection(modes.indexOf(layer.blend).coerceAtLeast(0)) }
        fields.addView(label("Name")); fields.addView(name); fields.addView(label("X position")); fields.addView(x)
        fields.addView(label("Y position")); fields.addView(y); fields.addView(opacityLabel); fields.addView(opacity,LinearLayout.LayoutParams(-1,dp(48)))
        fields.addView(label("Blend mode")); fields.addView(blend)
        val dialog=AlertDialog.Builder(this).setTitle("Layer properties").setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val left=x.text.toString().toIntOrNull(); val top=y.text.toString().toIntOrNull(); val title=name.text.toString().trim()
            if(title.isBlank()) name.error="Enter a layer name"
            else if(left==null) x.error="Enter a whole pixel coordinate"
            else if(top==null) y.error="Enter a whole pixel coordinate"
            else { dialog.dismiss(); applyEdit(Edit.LayerProperties(layer.id,title,layer.visible,opacity.progress,left,top,modes[blend.selectedItemPosition])) }
        } }; dialog.show()
    }
    private fun openLayerPicker() {
        pickerPending=true; editor.stop(); update()
        try { InfiniSelect.open(this,OpenRequest("infinicanvas","InfiniCanvas",mimeTypes=listOf("*/*")),REQUEST_LAYER) }
        catch(error:Exception) { pickerPending=false; editor.setMessage("Could not import image: ${error.message}") }
    }

    private fun slider(name:String,initial:Int,changed:(Int)->Unit) {
        val line=row()
        val caption=label("$name\n$initial",12f).apply { minWidth=dp(88) }
        line.addView(caption,LinearLayout.LayoutParams(dp(96),dp(48)))
        val seek=SeekBar(this).apply {
            max=200;progress=initial+100;minimumHeight=dp(48);contentDescription=name
            progressTintList=ColorStateList.valueOf(color(R.color.accent));thumbTintList=ColorStateList.valueOf(color(R.color.accent))
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view:SeekBar?,value:Int,fromUser:Boolean) { if(fromUser) { val amount=value-100;caption.text="$name\n${if(amount>0) "+" else ""}$amount";changed(amount);update() } }
                override fun onStartTrackingTouch(view:SeekBar?) {}
                override fun onStopTrackingTouch(view:SeekBar?) {}
            })
        }
        documentControls+=seek;line.addView(seek,LinearLayout.LayoutParams(0,dp(48),1f));toolPanel.addView(line)
    }
    private fun applyCancel(apply:()->Unit) {
        val actions=row()
        actions.addIcon(icon("close","Discard adjustments") { activateTool("view") })
        spacer(actions)
        actions.addIcon(actionIcon("check","Apply adjustments",apply).apply { isSelected=true })
        toolFooter.addView(actions)
    }
    private fun switchTool(next: String) {
        if(next==tool) { if(next!="crop") buildTools();return }
        if (tool=="crop" || (tool=="adjust" && !adjustment.isIdentity)) {
            AlertDialog.Builder(this).setTitle("Discard this preview?")
                .setMessage("Apply keeps the edit. Discard returns to your last applied image.")
                .setNegativeButton("Keep editing",null).setPositiveButton("Discard") { _,_ -> activateTool(next) }.show()
        } else activateTool(next)
    }
    private fun activateTool(next: String) {
        dismissToolDialog()
        if(next in setOf("crop","adjust","select") && !editor.state.frameMode) editor.stop() else editor.pause()
        if(tool in setOf("crop","adjust","select") || next in setOf("crop","adjust","select")) lastBitmap=null
        if(next=="frames" && editor.state.frameLayerMode) editor.showFrameLayers(false)
        tool=next; adjustment=Edit.Adjust(); preview.adjustment=adjustment
        preview.cropping=next=="crop"
        if(next=="crop") { cropLayerOnly=editor.state.layered && !editor.state.frameMode;preview.crop=Edit.Crop(0f,0f,1f,1f) }
        buildTools()
    }

    private fun update() {
        if (!::status.isInitialized) return
        val state=editor.state
        val locked=state.busy || pickerPending || savePending || state.export!=null
        val hasImage=state.bitmap!=null
        title.text=if(hasImage) state.name else "InfiniCanvas"
        detail.text=if(hasImage) "${state.width} × ${state.height} · ${if(state.dirty) "Unexported" else "Draft saved"}" else "Open or create an image"
        if(lastStatus!=state.message || lastStatusBusy!=state.busy) {
            lastStatus=state.message;lastStatusBusy=state.busy
            status.removeCallbacks(hideStatus);status.text=state.message
            status.visibility=if(state.busy || hasImage) View.VISIBLE else View.GONE
            if(!state.busy) status.postDelayed(hideStatus,3500)
        }
        progress.visibility=if(state.busy) View.VISIBLE else View.GONE
        emptyMessage.visibility=if(!hasImage && !state.busy) View.VISIBLE else View.GONE
        preview.isEnabled=hasImage
        preview.editingEnabled=!locked && !state.playing
        preview.animation(state.drawable.takeUnless { tool in setOf("crop","adjust","transform","select") },state.playing && !locked)
        transport.visibility=if(tool in setOf("crop","select")) View.GONE else View.VISIBLE
        cropActions.visibility=if(tool in setOf("crop","select")) View.VISIBLE else View.GONE
        playback.visibility=if(state.canAnimate) View.VISIBLE else View.GONE
        playback.symbol=if(state.playing) "pause" else "play"
        playback.label(if(state.playing) "Pause animation or color cycling" else "Play animation or color cycling")
        playback.isEnabled=state.canAnimate && !locked && tool !in setOf("crop","adjust","transform","select")
        playback.invalidate()
        val selectedLayer=state.layers.firstOrNull { it.id==state.selectedLayer }
        preview.layerBounds=if(tool=="layers" && !state.playing && selectedLayer!=null) android.graphics.RectF(selectedLayer.x.toFloat(),selectedLayer.y.toFloat(),(selectedLayer.x+selectedLayer.bitmap.width).toFloat(),(selectedLayer.y+selectedLayer.bitmap.height).toFloat()) else null
        preview.onLayerPick=if(tool=="layers" && (!state.frameMode || state.frameLayerMode)) { { x,y -> state.layers.firstOrNull { layer ->
            val px=x-layer.x;val py=y-layer.y
            layer.visible && layer.opacity>0 && px in 0 until layer.bitmap.width && py in 0 until layer.bitmap.height && (layer.bitmap.getPixel(px,py) ushr 24)>0
        }?.let { editor.selectLayer(it.id) } } } else null
        val selectedImage=if(state.layered && !state.playing && (tool in setOf("adjust","select") || (tool=="crop" && cropLayerOnly))) state.layers.firstOrNull { it.id==state.selectedLayer } else null
        val displayed=if(tool=="select") editor.selectionImage() else selectedImage?.bitmap ?: state.bitmap
        preview.selectionTool=if(tool=="select") selectionTool else null
        preview.selectionMask=if(tool=="select") state.selectionOverlay else null
        preview.selectionBounds=if(tool=="select") state.selectionBounds else null
        if(selectedImage!=null) detail.text="Editing ${selectedImage.name} · ${selectedImage.bitmap.width} × ${selectedImage.bitmap.height} px"
        if(lastBitmap!==displayed) {
            val reset=lastBitmap==null || lastBitmap?.width!=displayed?.width || lastBitmap?.height!=displayed?.height || lastDocumentName!=state.name
            lastBitmap=displayed;preview.display(displayed,reset && !state.playing)
        }
        if(tool=="crop") updateCropCaption()
        if(tool=="select") detail.text="${state.selectedPixels} pixels selected · ${selectionMode.replaceFirstChar { it.uppercase() }}"
        lastDocumentName=state.name
        openButton.isEnabled=!locked;moreButton.isEnabled=!locked
        exportButton.isEnabled=hasImage && !locked && tool!="crop" && (tool!="adjust" || adjustment.isIdentity)
        undoButton.isEnabled=hasImage && !locked && state.history.canUndo && tool!="adjust" && tool!="crop"
        redoButton.isEnabled=hasImage && !locked && state.history.canRedo && tool!="adjust" && tool!="crop"
        documentControls.forEach { it.isEnabled=hasImage && !locked && controlAvailability[it]!=false;it.alpha=if(it.isEnabled) 1f else .45f }
        frameEditControls.forEach { it.isEnabled=it.isEnabled && !state.playing;it.alpha=if(it.isEnabled) 1f else .45f }
        framePlayback?.let { it.symbol=if(state.playing) "pause" else "play";it.label(if(state.playing) "Pause animation" else "Play animation");it.isEnabled=state.canAnimate && !locked;it.invalidate() }
        updateFrameHighlight()
        cyclePlayback?.let { it.symbol=if(state.playing) "pause" else "play";it.label(if(state.playing) "Pause cycling" else "Play cycling");it.isEnabled=state.canAnimate && !locked;it.invalidate() }
        cycleRestart?.isEnabled=state.canAnimate && !locked
        selectionClear?.isEnabled=hasImage && !locked && state.selectedPixels>0
        selectionInvert?.isEnabled=hasImage && !locked && state.selection!=null
        tabs.forEach { (key,view) ->
            view.isEnabled=hasImage && !locked && (key !in setOf("select","adjust","transform","crop") || (state.media[state.selectedLayer]?.renderable!=false));view.isSelected=key==tool
            view.visibility=if((key=="layers" && state.frameMode && !state.frameLayerMode) || (key=="palette" && state.colors.isEmpty()) || (key=="cycles" && state.paletteFormat!="iff")) View.GONE else View.VISIBLE
        }
        if(!state.busy && toolDialog!=null && tool in setOf("palette","cycles","layers","frames") && (toolsHistory!==state.history || toolsLayer!=state.selectedLayer)) {
            buildTools(); return
        }
        if (!state.busy && pendingResult!=null) {
            val (request,code,data)=pendingResult!!; pendingResult=null
            receiveResult(request,code,data)
        } else if (!state.busy && state.export!=null && !savePending) {
            launchExport(state.export)
        }
        if(!state.busy && state.openFailure!=null) {
            editor.clearOpenFailure();showOpenError(state.openFailure)
        }
        if(!state.busy && state.openNotice!=null) {
            editor.clearOpenNotice();openNoticeDialog?.dismiss()
            openNoticeDialog=AlertDialog.Builder(this).setTitle("Image opened")
                .setMessage(state.openNotice).setPositiveButton("Done",null).create().also { it.show() }
        }
        if(!state.busy && deferredIncoming!=null) scheduleIncoming()
    }

    private fun replaceDraft(action: () -> Unit) {
        if (editor.state.busy || pickerPending || savePending) return
        if (editor.state.bitmap!=null && (editor.state.dirty || tool=="crop" || !adjustment.isIdentity)) {
            AlertDialog.Builder(this).setTitle("Replace this draft?")
                .setMessage("Export a copy first if you want to keep these edits. ORA keeps layers; indexed PNG/IFF keeps palette edits. The current draft will be replaced after a new image opens successfully.")
                .setNegativeButton("Keep editing",null).setPositiveButton("Continue") { _,_ -> action() }.show()
        } else action()
    }
    private fun openPicker() {
        pickerPending=true; update()
        try { InfiniSelect.open(this,OpenRequest(clientId="infinicanvas",displayName="InfiniCanvas",
            mimeTypes=listOf("*/*"), supportedExtensions=com.infinifolder.preview.CanvasCapabilities.extensions.sorted()),REQUEST_OPEN) }
        catch (error: Exception) { pickerPending=false; editor.setMessage("Could not open Files: ${error.message}") }
    }
    private fun exportInfo() {
        editor.pause()
        val state=editor.state
        val writers=com.infinifolder.preview.ImageSaving.formats.filter { it.id!="icanim" && (it.id!="apng" || state.frameMode) }
        val source=if(state.frameMode) "icanim" else if(state.media.isNotEmpty()) "ora" else com.infinifolder.preview.ImageSaving.format(state.sourceFormat)?.id
        val choices=writers.sortedBy { if(it.id==source) 0 else if(it.id=="png") 1 else if(it.id=="ora") 2 else 3 }
            .map { f -> "${f.id.uppercase()} · ${if(f.id==source) "source format · " else ""}${if(f.layers) "raster layers" else if(f.animation && state.frameMode) "animation" else "still image"}" to f.id }.toMutableList()
        if(state.frameMode) choices.add(0,"ICANIM · editable animation project" to "icanim")
        if(state.palettePreservable) choices.add(0,"${state.paletteFormat!!.uppercase()} · original indices & palette" to "indexed")
        var selected=0
        AlertDialog.Builder(this).setTitle(if(source==null) "Export a copy · source has no writer" else "Export a copy")
            .setSingleChoiceItems(choices.map { it.first }.toTypedArray(),0) { _,which -> selected=which }
            .setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_ ->
                val format=choices[selected].second
                val message=when(format) {
                    "icanim" -> "Save the timeline, frame durations, loop count and each frame’s layered ORA document. The .icanim file is a ZIP with animation.xml and ORA frames."
                    "apng","gif" -> "Save ${if(state.frameMode) "the visible frames with timing and loop count" else "the current still"}. ${com.infinifolder.preview.ImageSaving.format(format)?.limitations.orEmpty()} Layer names, source metadata, and edit history are omitted."
                    "indexed" -> "Keep the original encoded pixels and edited palette. IFF exports include cycling ranges."
                    "ora" -> if(state.frameMode) "Save only the selected frame’s ORA document and layers. Choose ICANIM to save the complete animation." else if(state.media.isNotEmpty()) "Save every media layer with its encoded source, palette/cycle edits and retained extension metadata. Pixel edits use a PNG working representation; non-PNG originals remain embedded." else "Save the working layers as an OpenRaster container."
                    "psd","xcf" -> "Save supported raster layers, names, order, positions, visibility and opacity. Embedded non-raster files and ORA extensions are omitted. ${com.infinifolder.preview.ImageSaving.format(format)?.limitations.orEmpty()}"
                    else -> "Save the full working raster as ${format.uppercase()}. Layers, animation, palette identity, cycling and source metadata are omitted. ${com.infinifolder.preview.ImageSaving.format(format)?.limitations.orEmpty()}"
                }
                AlertDialog.Builder(this).setTitle(choices[selected].first).setMessage(message+" Your source stays unchanged.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Choose location") { _,_ -> editor.prepareExport(format) }.show()
            }.show()
    }
    private fun launchExport(file: File) {
        savePending=true; exportFile=file; update()
        try {
            val stem=editor.state.name.substringBeforeLast('.',editor.state.name)
                .replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(100).ifBlank { "canvas" }
            val extension=file.extension
            val name="$stem-edited.$extension"
            val mime=if(extension=="icanim") com.infinifolder.preview.AnimationProjects.MIME_TYPE else com.infinifolder.preview.ImageSaving.format(extension)?.mimeType ?: "image/png"
            val uri=InfiniSelect.createFileDocument(this,file,name,mime)
            InfiniSelect.save(this,SaveRequest("infinicanvas","InfiniCanvas",name,mime,uri,file.length()),REQUEST_SAVE)
        } catch (error: Exception) {
            savePending=false; exportFile?.delete(); exportFile=null
            editor.finishExport(false,"Could not start export: ${error.message}")
        }
    }

    @Deprecated("Platform result API matches the standalone selector contract.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode in setOf(REQUEST_OPEN,REQUEST_LAYER) && !pickerPending) return
        if(editor.state.busy) { pendingResult=Triple(requestCode,resultCode,data); return }
        receiveResult(requestCode,resultCode,data)
    }
    private fun receiveResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            REQUEST_LAYER -> {
                pickerPending=false
                if(resultCode==RESULT_OK && data!=null) try {
                    StorageAccess.persist(this,data)
                    val item=InfiniSelect.readResult(this,data).items.firstOrNull() ?: error("No image selected")
                    editor.importLayer(item.contentUri,item.authoritativeUri,item.name)
                } catch(error:Exception) { editor.setMessage("Could not import layer: ${error.message}") }
                else editor.setMessage("Layer import cancelled.")
            }
            REQUEST_OPEN -> {
                pickerPending=false
                if (resultCode==RESULT_OK && data!=null) {
                    try {
                        StorageAccess.persist(this,data)
                        val item=InfiniSelect.readResult(this,data).items.firstOrNull() ?: error("No image selected")
                        activateTool("view")
                        editor.open(item.contentUri,item.authoritativeUri,item.name)
                    } catch (error: Exception) { editor.setMessage("Could not open image: ${error.message}") }
                } else editor.setMessage("Open cancelled. Your draft is kept.")
            }
            REQUEST_SAVE -> {
                savePending=false
                val success=resultCode==RESULT_OK && data?.getBooleanExtra(FileSelectionContract.EXTRA_SAVE_SUCCESS,false)==true
                val name=data?.getStringExtra(FileSelectionContract.EXTRA_SAVE_RESULT_NAME) ?: "PNG copy"
                val failure=data?.getStringExtra(FileSelectionContract.EXTRA_SAVE_ERROR)
                exportFile?.delete(); exportFile=null
                editor.finishExport(success,if(success) "Exported $name. Your source is unchanged." else failure?.let { "Export failed: $it" } ?: "Export cancelled. Your draft is kept.")
            }
        }
        update()
    }

    private fun dimensionInput(hint: String, value: Int) = EditText(this).apply {
        this.hint=hint; setText(value.toString()); inputType=InputType.TYPE_CLASS_NUMBER
        minHeight=dp(48); setSelectAllOnFocus(true); contentDescription=hint
    }
    private fun newCanvasDialog() {
        val fields=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        fields.addView(label("Enter pixel dimensions. Available device memory determines how large a canvas can be.",13f,true))
        val w=dimensionInput("Width in pixels",1080); val h=dimensionInput("Height in pixels",1080)
        fields.addView(label("Width")); fields.addView(w); fields.addView(label("Height")); fields.addView(h)
        val transparent=CheckBox(this).apply { text="Transparent background"; isChecked=true; minHeight=dp(48) }; fields.addView(transparent)
        val dialog=AlertDialog.Builder(this).setTitle("New canvas").setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("Cancel",null).setPositiveButton("Create",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val width=w.text.toString().toIntOrNull(); val height=h.text.toString().toIntOrNull()
            if(width==null || width<=0) w.error="Enter a positive pixel count"
            else if(height==null || height<=0) h.error="Enter a positive pixel count"
            else { dialog.dismiss(); activateTool("view"); editor.newCanvas(width,height,transparent.isChecked) }
        } }; dialog.show()
    }
    private fun resizeDialog() {
        if(editor.state.bitmap==null) return
        editor.pause()
        val state=editor.state
        val layer=state.layers.firstOrNull { it.id==state.selectedLayer }
        val fields=column().apply { setPadding(dp(16),dp(4),dp(16),0) }
        val only=CheckBox(this).apply { text="Selected layer only";minHeight=dp(48);isChecked=false;visibility=if(state.layered && (!state.frameMode || state.frameLayerMode)) View.VISIBLE else View.GONE }
        fields.addView(only)
        fun syncScope() { only.isEnabled=!state.frameMode || !applyAllFrames;if(!only.isEnabled) only.isChecked=false }
        syncScope();frameScope(fields) { syncScope() }
        if(state.frameMode) fields.addView(label("Unchecked edits only this frame’s contents; the shared animation canvas stays the same size.",12f,true))
        fun base()=if(only.isChecked && layer!=null) layer.bitmap.width to layer.bitmap.height else state.width to state.height
        val w=dimensionInput("Width in pixels",state.width);val h=dimensionInput("Height in pixels",state.height)
        val sizes=row();sizes.addView(inputColumn("Width (px)",w),LinearLayout.LayoutParams(0,-2,1f));sizes.addView(inputColumn("Height (px)",h),LinearLayout.LayoutParams(0,-2,1f));fields.addView(sizes)
        val keep=CheckBox(this).apply { text="Keep aspect ratio";isChecked=true;minHeight=dp(48) };fields.addView(keep)
        val percent=EditText(this).apply { minHeight=dp(48);inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setText("100");contentDescription="Scale percentage" }
        val percentages=row();percentages.addView(label("Scale %",13f));percentages.addView(percent,LinearLayout.LayoutParams(0,dp(48),1f));fields.addView(percentages)
        val smooth=CheckBox(this).apply { text="Smooth resampling";isChecked=state.colors.isEmpty();minHeight=dp(48) };fields.addView(smooth)
        fields.addView(label("Turn smoothing off for nearest-neighbor pixel art. Image dimensions are limited by available device memory.",12f,true))
        var syncing=false
        fun watch(field:EditText,change:()->Unit) { field.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) { if(!syncing) { syncing=true;try { change() } finally { syncing=false } } }
        }) }
        watch(w) { w.text.toString().toIntOrNull()?.takeIf { it>0 }?.let { value -> val size=base();if(keep.isChecked) h.setText(kotlin.math.round(value.toDouble()*size.second/size.first).toInt().coerceAtLeast(1).toString());percent.setText(String.format(java.util.Locale.ROOT,"%.2f",value*100.0/size.first)) } }
        watch(h) { h.text.toString().toIntOrNull()?.takeIf { it>0 }?.let { value -> val size=base();if(keep.isChecked) w.setText(kotlin.math.round(value.toDouble()*size.first/size.second).toInt().coerceAtLeast(1).toString());percent.setText(String.format(java.util.Locale.ROOT,"%.2f",value*100.0/size.second)) } }
        watch(percent) { percent.text.toString().toDoubleOrNull()?.takeIf { it.isFinite() && it>0 }?.let { value -> val size=base();w.setText(kotlin.math.round(size.first*value/100).toInt().coerceAtLeast(1).toString());h.setText(kotlin.math.round(size.second*value/100).toInt().coerceAtLeast(1).toString()) } }
        only.setOnCheckedChangeListener { _,_ -> syncing=true;val size=base();w.setText(size.first.toString());h.setText(size.second.toString());percent.setText("100");syncing=false }
        val dialog=AlertDialog.Builder(this).setTitle("Scale image").setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("Cancel",null).setPositiveButton("Scale",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val width=w.text.toString().toIntOrNull();val height=h.text.toString().toIntOrNull()
            if(width==null || width<=0) w.error="Enter a positive pixel count"
            else if(height==null || height<=0) h.error="Enter a positive pixel count"
            else if(width.toLong()*height>Int.MAX_VALUE/4) w.error="Exceeds Android’s addressable RGBA bitmap size"
            else { val edit=if(only.isChecked) Edit.Resize(width,height,smooth.isChecked) else Edit.ScaleImage(width,height,smooth.isChecked);dialog.dismiss();activateTool("view");applyEdit(edit) }
        } };dialog.show()
    }
    private fun inputColumn(caption:String,field:EditText)=column().apply { setPadding(dp(3),0,dp(3),0);addView(label(caption,12f,true));addView(field,LinearLayout.LayoutParams(-1,dp(48))) }
    private fun updateCropCaption() { if(::preview.isInitialized) { val b=preview.cropPixelBounds();cropCaption?.text="${b.width()} × ${b.height()}\n${if(editor.state.frameMode) if(applyAllFrames) "All frames" else "This frame" else "${b.left}, ${b.top}"}" } }
    private fun applyPixelCrop(bounds:android.graphics.Rect) {
        val edit=if(editor.state.layered && cropLayerOnly) Edit.PixelCrop(bounds.left,bounds.top,bounds.width(),bounds.height())
            else Edit.CropImage(bounds.left,bounds.top,bounds.width(),bounds.height())
        activateTool("view");applyEdit(edit)
    }
    private fun anchorCropPresets() {
        PopupMenu(this,cropActions.getChildAt(1)).apply {
            val values=listOf("Full image" to null,"Square 1:1" to 1f,"4:3" to 4f/3f,"16:9" to 16f/9f)
            values.forEachIndexed { i,pair -> menu.add(0,i,i,pair.first) }
            if(editor.state.frameMode) menu.add(0,100,100,"Apply to all frames").apply { isCheckable=true;isChecked=applyAllFrames }
            setOnMenuItemClickListener { if(it.itemId==100) { applyAllFrames=!it.isChecked;if(applyAllFrames && cropLayerOnly) { cropLayerOnly=false;preview.crop=Edit.Crop(0f,0f,1f,1f);update() };updateCropCaption() } else preview.preset(values[it.itemId].second);true };show()
        }
    }
    private fun pixelBoundsDialog(selection:Boolean) {
        fun size():Pair<Int,Int> {
            val state=editor.state
            return if(selection || (state.layered && cropLayerOnly)) editor.selectionImage()!!.let { it.width to it.height } else state.width to state.height
        }
        val original=if(selection) editor.state.selectionBounds ?: android.graphics.Rect(0,0,size().first,size().second) else preview.cropPixelBounds()
        val body=column().apply { setPadding(dp(16),0,dp(16),0) }
        val scope=CheckBox(this).apply { text="Selected layer only";minHeight=dp(48);isChecked=cropLayerOnly;visibility=if(!selection && editor.state.layered && (!editor.state.frameMode || editor.state.frameLayerMode)) View.VISIBLE else View.GONE }
        if(!selection) body.addView(scope)
        if(!selection) {
            fun syncScope() { scope.isEnabled=!editor.state.frameMode || !applyAllFrames;if(!scope.isEnabled) { scope.isChecked=false;cropLayerOnly=false } }
            syncScope();frameScope(body) { syncScope() }
            if(editor.state.frameMode) body.addView(label("Unchecked crops this frame’s contents; the shared animation canvas stays the same size.",12f,true))
        }
        val x=dimensionInput("Left X",original.left);val y=dimensionInput("Top Y",original.top)
        val w=dimensionInput("Width",original.width());val h=dimensionInput("Height",original.height())
        val origin=row();origin.addView(inputColumn("X (px)",x),LinearLayout.LayoutParams(0,-2,1f));origin.addView(inputColumn("Y (px)",y),LinearLayout.LayoutParams(0,-2,1f));body.addView(origin)
        val sizes=row();sizes.addView(inputColumn("Width (px)",w),LinearLayout.LayoutParams(0,-2,1f));sizes.addView(inputColumn("Height (px)",h),LinearLayout.LayoutParams(0,-2,1f));body.addView(sizes)
        body.addView(label("Coordinates start at 0. A width and height of 1 targets one pixel.",12f,true))
        scope.setOnCheckedChangeListener { _,checked -> cropLayerOnly=checked;preview.crop=Edit.Crop(0f,0f,1f,1f);update();val size=size();x.setText("0");y.setText("0");w.setText(size.first.toString());h.setText(size.second.toString()) }
        val dialog=AlertDialog.Builder(this).setTitle(if(selection) "Selection in pixels" else "Crop in pixels").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel",null).setPositiveButton(if(selection) "Select" else "Crop",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val left=x.text.toString().toIntOrNull();val top=y.text.toString().toIntOrNull();val width=w.text.toString().toIntOrNull();val height=h.text.toString().toIntOrNull();val size=size()
            if(left==null || left !in 0 until size.first) x.error="Enter 0–${size.first-1}"
            else if(top==null || top !in 0 until size.second) y.error="Enter 0–${size.second-1}"
            else if(width==null || width !in 1..size.first-left) w.error="Enter 1–${size.first-left}"
            else if(height==null || height !in 1..size.second-top) h.error="Enter 1–${size.second-top}"
            else { dialog.dismiss();if(selection) editor.selectPixels(left,top,width,height,selectionTool=="ellipse",selectionMode) else applyPixelCrop(android.graphics.Rect(left,top,left+width,top+height)) }
        } };dialog.show()
    }
    private fun selectionToolMenu() {
        PopupMenu(this,cropActions.getChildAt(1)).apply {
            val keys=listOf("rect","ellipse","lasso","wand","color","background")
            val names=listOf("Rectangle","Ellipse","Freehand lasso","Magic wand · connected color","Similar colors · whole layer","Detect background")
            names.forEachIndexed { i,name -> menu.add(0,i,i,name) }
            setOnMenuItemClickListener {
                if(keys[it.itemId]=="background") editor.selectBackground(selectionTolerance,selectionMode)
                else { selectionTool=keys[it.itemId];buildTools() }
                true
            };show()
        }
    }
    private fun selectionOptionsDialog() {
        val content=column().apply { setPadding(dp(16),0,dp(16),0) }
        val count=editor.state.selectedPixels
        frameScope(content)
        content.addView(label("$count pixels selected",13f,true))
        val operations=listOf("Replace","Add","Subtract","Intersect");val keys=listOf("replace","add","subtract","intersect")
        val mode=Spinner(this).apply { minimumHeight=dp(48);contentDescription="Selection combination";adapter=ArrayAdapter(this@CanvasActivity,android.R.layout.simple_spinner_dropdown_item,operations);setSelection(keys.indexOf(selectionMode).coerceAtLeast(0)) }
        content.addView(mode)
        val caption=label("Color tolerance · $selectionTolerance / 255",13f)
        val tolerance=SeekBar(this).apply { max=255;progress=selectionTolerance;minHeight=dp(48);contentDescription="Color selection tolerance" }
        tolerance.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view:SeekBar?,value:Int,fromUser:Boolean) { caption.text="Color tolerance · $value / 255" }
            override fun onStartTrackingTouch(view:SeekBar?) {}
            override fun onStopTrackingTouch(view:SeekBar?) {}
        });content.addView(caption);content.addView(tolerance)
        content.addView(label("Background detection selects the dominant border color connected to an edge. Review the highlight before clearing. Settings apply to the next selection.",12f,true))
        val actions=row()
        lateinit var dialog:AlertDialog
        fun settings() { selectionTolerance=tolerance.progress;selectionMode=keys[mode.selectedItemPosition] }
        actions.addIcon(icon("selectAll","Select all pixels") { settings();dialog.dismiss();editor.selectAll() })
        actions.addIcon(icon("deselect","Deselect all") { settings();dialog.dismiss();editor.clearSelection() })
        actions.addIcon(icon("background","Detect and select background") { settings();dialog.dismiss();editor.selectBackground(selectionTolerance,selectionMode) })
        editor.state.selectionBounds?.let { b -> actions.addIcon(icon("crop","Crop to selection bounds") { dialog.dismiss();activateTool("view");applyEdit(Edit.PixelCrop(b.left,b.top,b.width(),b.height())) }) }
        content.addView(actions)
        dialog=AlertDialog.Builder(this).setTitle("Selection settings").setView(ScrollView(this).apply { addView(content) }).setNegativeButton("Cancel",null).setPositiveButton("Done") { _,_ -> settings();update() }.create();dialog.show()
    }

    private fun chooseTheme() {
        val options=arrayOf("Follow device","Light","Dark")
        val current=preferences.getInt("theme",0)
        AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(options,current) { dialog,which ->
            preferences.edit().putInt("theme",which).apply(); dialog.dismiss()
            if(which!=current) recreate()
        }.setNegativeButton("Close",null).show()
    }
    override fun attachBaseContext(base: Context) {
        val selected=base.getSharedPreferences("appearance",MODE_PRIVATE).getInt("theme",0)
        if (selected==0) super.attachBaseContext(base)
        else {
            val config=Configuration(base.resources.configuration)
            config.uiMode=(config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if(selected==2) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            super.attachBaseContext(base.createConfigurationContext(config))
        }
    }
    private fun handleBack() {
        if(editor.state.busy || savePending || pickerPending) return
        if(toolDialog!=null) toolDialog?.dismiss()
        else if(tool in setOf("crop","adjust","select")) activateTool("view")
        else if(tool=="layers") activateTool("view")
        else finish()
    }
    @Deprecated("Legacy devices use platform back handling.")
    override fun onBackPressed() = handleBack()
    companion object { private const val REQUEST_OPEN=8101; private const val REQUEST_SAVE=8102; private const val REQUEST_LAYER=8103 }
}
