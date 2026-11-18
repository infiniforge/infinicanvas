package com.infinicanvas

import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    @Test fun undoThenEditDropsOnlyTheFutureBranch() {
        val history=EditHistory().append(Edit.Turn()).append(Edit.Flip()).append(Edit.Adjust(brightness=20))
        val branch=history.undo().undo().append(Edit.Crop(0f,.25f,1f,.75f))
        assertEquals(listOf(Edit.Turn(),Edit.Crop(0f,.25f,1f,.75f)),branch.active)
        assertFalse(branch.canRedo)
        assertEquals(3,history.cursor)
    }
    @Test fun reopeningAnUndoneDraftCanStillRedo() {
        val stored=listOf(Edit.Resize(640,480),Edit.Turn(),Edit.Adjust(saturation=-100))
        val recovered=EditHistory(stored,1)
        assertEquals(listOf(Edit.Resize(640,480)),recovered.active)
        assertEquals(stored,recovered.redo().redo().active)
        assertEquals(recovered,recovered.redo().undo())
    }
    @Test fun editsNeverIncludeTheOriginalImageAsAnOperation() {
        val history=EditHistory().append(Edit.Turn())
        assertEquals(emptyList<Edit>(),history.undo().active)
        assertFalse(history.undo().canUndo)
        assertEquals(history.undo(),history.undo().undo())
        assertEquals(history,history.redo())
    }
    @Test fun historyBeyondTheFormerLimitAllowsAppendUndoAndReplacement() {
        // A small fixed fixture exercises the removed 200-edit quota without allocating
        // according to an implementation sentinel or an available-memory limit.
        val history=EditHistory(List(512) { Edit.Turn() })
        val extended=history.append(Edit.Adjust(brightness=10))
        assertEquals(513,extended.cursor)
        val branch=extended.undo().append(Edit.Flip())
        assertEquals(513,branch.cursor)
        assertEquals(Edit.Flip(),branch.active.last())
        assertEquals(Edit.Adjust(brightness=10),extended.active.last())
        assertEquals(history.active,branch.undo().active)
        assertFalse(branch.canRedo)
    }
    @Test fun redoAtTheLargestRepresentableCursorDoesNotOverflow() {
        // Virtual collection: this fixture stores one operation, not billions of entries.
        val operation=Edit.Turn()
        val edits=object:AbstractList<Edit>() {
            override val size=Int.MAX_VALUE
            override fun get(index:Int):Edit {
                require(index in indices)
                return operation
            }
        }
        val history=EditHistory(edits)
        assertFalse(history.canRedo)
        assertSame(history,history.redo())
        assertEquals(Int.MAX_VALUE,history.undo().redo().cursor)
    }
    @Test fun cropRejectsNonfiniteReversedAndEmptyBounds() {
        for (bounds in listOf(floatArrayOf(Float.NaN,0f,1f,1f),floatArrayOf(0f,0f,Float.POSITIVE_INFINITY,1f),
            floatArrayOf(-.01f,0f,1f,1f),floatArrayOf(.5f,0f,.5f,1f),floatArrayOf(.9f,0f,.2f,1f))) {
            assertThrows(IllegalArgumentException::class.java) { Edit.Crop(bounds[0],bounds[1],bounds[2],bounds[3]) }
        }
        assertEquals(Edit.Crop(0f,0f,1f,1f),Edit.Crop(0f,0f,1f,1f))
    }
    @Test fun invalidDraftCursorsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { EditHistory(emptyList(),-1) }
        assertThrows(IllegalArgumentException::class.java) { EditHistory(emptyList(),1) }
        assertThrows(IllegalArgumentException::class.java) { EditHistory(listOf(Edit.Turn()),2) }
    }
    @Test fun dimensionsBeyondTheFormerEditorLimitAreAccepted() {
        assertEquals(2049,Edit.Resize(2049,100).width)
        assertEquals(8192,Edit.Resize(8192,4096).width)
        assertEquals(8192,Edit.ScaleImage(8192,4096).width)
        assertEquals(4096,Edit.PixelCrop(100,200,8192,4096).height)
        assertEquals(8192,Edit.CropImage(100,200,8192,4096).width)
        assertEquals(8192,Edit.ClearPixels("asset-1234-abcd.png",8192,4096).width)
    }
    @Test fun nonpositiveAndUnrepresentableDimensionsAreRejected() {
        for((width,height) in listOf(0 to 100,100 to 0,-1 to 100,100 to -1,
            Int.MAX_VALUE to Int.MAX_VALUE,536870912 to 1)) {
            assertThrows(IllegalArgumentException::class.java) { Edit.Resize(width,height) }
            assertThrows(IllegalArgumentException::class.java) { Edit.ScaleImage(width,height) }
            assertThrows(IllegalArgumentException::class.java) { Edit.PixelCrop(0,0,width,height) }
            assertThrows(IllegalArgumentException::class.java) { Edit.CropImage(0,0,width,height) }
            assertThrows(IllegalArgumentException::class.java) { Edit.ClearPixels("asset-1234-abcd.png",width,height) }
        }
        // Only a recipe is constructed; no bitmap or pixel buffer is allocated.
        assertEquals(536870911,Edit.Resize(536870911,1).width)
    }
    @Test fun pixelCropRejectsNegativeAndOverflowingCoordinates() {
        for((x,y) in listOf(-1 to 0,0 to -1,Int.MAX_VALUE to 0,0 to Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { Edit.PixelCrop(x,y,1,1) }
            assertThrows(IllegalArgumentException::class.java) { Edit.CropImage(x,y,1,1) }
        }
        assertEquals(Int.MAX_VALUE-1,Edit.PixelCrop(Int.MAX_VALUE-1,0,1,1).x)
    }
    @Test fun invalidAdjustmentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Edit.Adjust(contrast=101) }
    }
}
