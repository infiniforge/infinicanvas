package com.infinicanvas

import org.json.JSONArray
import org.json.JSONObject

object EditCodec {
    fun write(edits:List<Edit>)=JSONArray().apply { edits.forEach { put(encode(it)) } }
    fun read(array:JSONArray):List<Edit> {
        return List(array.length()) { decode(array.getJSONObject(it)) }
    }
    private fun encode(edit:Edit):JSONObject=when(edit) {
        is Edit.PixelCrop -> JSONObject().put("type","pixelCrop").put("x",edit.x).put("y",edit.y).put("width",edit.width).put("height",edit.height)
        is Edit.CropImage -> JSONObject().put("type","cropImage").put("x",edit.x).put("y",edit.y).put("width",edit.width).put("height",edit.height)
        is Edit.ScaleImage -> JSONObject().put("type","scaleImage").put("width",edit.width).put("height",edit.height).put("smooth",edit.smooth)
        is Edit.ClearPixels -> JSONObject().put("type","clearPixels").put("file",edit.file).put("width",edit.width).put("height",edit.height)
        is Edit.FrameOperation -> JSONObject().put("type","frameOperation").put("frame",edit.frameId ?: "").put("layer",edit.layerId ?: "").put("operation",encode(edit.operation))
        is Edit.FrameEdit -> JSONObject().put("type","frameEdit").put("id",edit.id).put("operation",encode(edit.operation))
        is Edit.FrameLoops -> JSONObject().put("type","frameLoops").put("plays",edit.plays)
        is Edit.FrameMode -> JSONObject().put("type","frameMode").put("enabled",edit.enabled)
        is Edit.FrameDelay -> JSONObject().put("type","frameDelay").put("id",edit.id).put("millis",edit.millis)
        is Edit.Turn -> JSONObject().put("type","turn").put("clockwise",edit.clockwise)
        is Edit.Flip -> JSONObject().put("type","flip").put("horizontal",edit.horizontal)
        is Edit.Crop -> JSONObject().put("type","crop").put("left",edit.left).put("top",edit.top).put("right",edit.right).put("bottom",edit.bottom)
        is Edit.Resize -> JSONObject().put("type","resize").put("width",edit.width).put("height",edit.height).put("smooth",edit.smooth)
        is Edit.Adjust -> JSONObject().put("type","adjust").put("brightness",edit.brightness).put("contrast",edit.contrast).put("saturation",edit.saturation)
        is Edit.PaletteColor -> JSONObject().put("type","paletteColor").put("index",edit.index).put("argb",edit.argb)
        is Edit.PaletteShift -> JSONObject().put("type","paletteShift").put("first",edit.first).put("last",edit.last).put("steps",edit.steps)
        is Edit.CycleChange -> JSONObject().put("type","cycle").put("index",edit.index).put("first",edit.first).put("last",edit.last).put("mhz",edit.millihertz).put("reverse",edit.reverse).put("enabled",edit.enabled)
        is Edit.LayerProperties -> JSONObject().put("type","layerProperties").put("id",edit.id).put("name",edit.name).put("visible",edit.visible).put("opacity",edit.opacity).put("x",edit.x).put("y",edit.y).put("blend",edit.blend)
        is Edit.LayerMove -> JSONObject().put("type","layerMove").put("id",edit.id).put("position",edit.position)
        is Edit.LayerDuplicate -> JSONObject().put("type","layerDuplicate").put("id",edit.id).put("newId",edit.newId)
        is Edit.LayerDelete -> JSONObject().put("type","layerDelete").put("id",edit.id)
        is Edit.LayerNew -> JSONObject().put("type","layerNew").put("id",edit.id).put("name",edit.name)
        is Edit.MediaImport -> JSONObject().put("type","mediaImport").put("id",edit.id).put("file",edit.file).put("name",edit.name).put("extension",edit.extension).put("mime",edit.mime ?: "")
        is Edit.LayerImport -> JSONObject().put("type","layerImport").put("id",edit.id).put("file",edit.file).put("name",edit.name)
        is Edit.OnLayer -> JSONObject().put("type","onLayer").put("id",edit.id).put("operation",encode(edit.operation))
    }
    private fun decode(j:JSONObject):Edit=when(j.getString("type")) {
        "pixelCrop" -> Edit.PixelCrop(j.getInt("x"),j.getInt("y"),j.getInt("width"),j.getInt("height"))
        "cropImage" -> Edit.CropImage(j.getInt("x"),j.getInt("y"),j.getInt("width"),j.getInt("height"))
        "scaleImage" -> Edit.ScaleImage(j.getInt("width"),j.getInt("height"),j.optBoolean("smooth",true))
        "clearPixels" -> Edit.ClearPixels(j.getString("file"),j.getInt("width"),j.getInt("height"))
        "frameOperation" -> Edit.FrameOperation(j.optString("frame").takeIf { it.isNotBlank() },decode(j.getJSONObject("operation")),j.optString("layer").takeIf { it.isNotBlank() })
        "frameEdit" -> Edit.FrameEdit(j.getString("id"),decode(j.getJSONObject("operation")))
        "frameLoops" -> Edit.FrameLoops(j.getInt("plays"))
        "frameMode" -> Edit.FrameMode(j.getBoolean("enabled"))
        "frameDelay" -> Edit.FrameDelay(j.getString("id"),j.getInt("millis"))
        "turn" -> Edit.Turn(j.getBoolean("clockwise"))
        "flip" -> Edit.Flip(j.getBoolean("horizontal"))
        "crop" -> Edit.Crop(j.getDouble("left").toFloat(),j.getDouble("top").toFloat(),j.getDouble("right").toFloat(),j.getDouble("bottom").toFloat())
        "resize" -> Edit.Resize(j.getInt("width"),j.getInt("height"),j.optBoolean("smooth",true))
        "adjust" -> Edit.Adjust(j.getInt("brightness"),j.getInt("contrast"),j.getInt("saturation"))
        "paletteColor" -> Edit.PaletteColor(j.getInt("index"),j.getInt("argb"))
        "paletteShift" -> Edit.PaletteShift(j.getInt("first"),j.getInt("last"),j.getInt("steps"))
        "cycle" -> Edit.CycleChange(j.getInt("index"),j.getInt("first"),j.getInt("last"),j.getInt("mhz"),j.getBoolean("reverse"),j.getBoolean("enabled"))
        "layerProperties" -> Edit.LayerProperties(j.getString("id"),j.getString("name"),j.getBoolean("visible"),j.getInt("opacity"),j.getInt("x"),j.getInt("y"),j.getString("blend"))
        "layerMove" -> Edit.LayerMove(j.getString("id"),j.getInt("position"))
        "layerDuplicate" -> Edit.LayerDuplicate(j.getString("id"),j.getString("newId"))
        "layerDelete" -> Edit.LayerDelete(j.getString("id"))
        "layerNew" -> Edit.LayerNew(j.getString("id"),j.getString("name"))
        "mediaImport" -> Edit.MediaImport(j.getString("id"),j.getString("file"),j.getString("name"),j.optString("extension"),j.optString("mime").takeIf { it.isNotBlank() })
        "layerImport" -> Edit.LayerImport(j.getString("id"),j.getString("file"),j.getString("name"))
        "onLayer" -> Edit.OnLayer(j.getString("id"),decode(j.getJSONObject("operation")))
        else -> error("Unknown draft edit")
    }
}
