package com.infinicanvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.infinifolder.preview.BitmapFrame
import com.infinifolder.preview.ImageCropBounds
import com.infinifolder.preview.ImageOrientationChange
import com.infinifolder.preview.ImageTransform
import com.infinifolder.preview.ImageTransforms

/** Geometry/encoding use Canvas public APIs; color-preview policy belongs to the editor. */
object ImageRenderer {
    fun colors(edit: Edit.Adjust): ColorMatrix {
        val contrast = 1f + edit.contrast / 100f
        val offset = 128f * (1f - contrast) + edit.brightness * 2.55f
        val tone = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ))
        return ColorMatrix().apply { setSaturation(1f + edit.saturation / 100f); postConcat(tone) }
    }

    fun render(base: Bitmap, edits: List<Edit>): Bitmap {
        var current = base
        try {
            for (edit in edits) {
                val next = when (edit) {
                    is Edit.Turn -> transform(current, ImageTransform.Orient(if (edit.clockwise)
                        ImageOrientationChange.ROTATE_CLOCKWISE else ImageOrientationChange.ROTATE_COUNTERCLOCKWISE))
                    is Edit.Flip -> transform(current, ImageTransform.Orient(if (edit.horizontal)
                        ImageOrientationChange.FLIP_HORIZONTAL else ImageOrientationChange.FLIP_VERTICAL))
                    is Edit.Crop -> transform(current, ImageTransform.Crop(ImageCropBounds(
                        edit.left * current.width, edit.top * current.height,
                        edit.right * current.width, edit.bottom * current.height)))
                    is Edit.PixelCrop -> {
                        require(edit.x+edit.width<=current.width && edit.y+edit.height<=current.height) { "Crop exceeds image pixels" }
                        transform(current,ImageTransform.Crop(ImageCropBounds(edit.x.toFloat(),edit.y.toFloat(),(edit.x+edit.width).toFloat(),(edit.y+edit.height).toFloat())))
                    }
                    is Edit.Resize -> transform(current, ImageTransform.Scale(edit.width, edit.height,edit.smooth))
                    is Edit.Adjust -> if (edit.isIdentity) current else {
                        val output = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
                        try {
                            Canvas(output).drawBitmap(current, 0f, 0f,
                                Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colors(edit)) })
                            output
                        } catch (error: Throwable) { output.recycle(); throw error }
                    }
                    else -> error("Document operation requires DocumentRenderer")
                }
                if (current !== base && current !== next) current.recycle()
                current = next
            }
            return current
        } catch (error: Throwable) {
            if (current !== base) current.recycle()
            throw error
        }
    }

    private fun transform(input: Bitmap, edit: ImageTransform): Bitmap =
        BitmapFrame(input).use { frame -> ImageTransforms.apply(frame, edit).use { it.takeBitmap() } }
}
