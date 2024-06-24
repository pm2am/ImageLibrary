package com.example.image_library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.sqrt

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0) : AppCompatImageView(context, attributeSet, defStyleAttr) {

    private var mode = NONE

    private var start = PointF()
    private var mid = PointF()

    private var oldDist = 1F

    private var imgW = 0
    private var imgH = 0

    private var matrix = Matrix()
    private var savedMatrix = Matrix()
    private var tmpMatrix = Matrix()

    private var shownImageSize = FloatArray(2)
    private var matrixValue = FloatArray(9)
    private var initialImageSize = FloatArray(2)
    private var corners = FloatArray(4)

    private var isInitialSizeSaved = false
    private var isZoomEnabled = true
    private var isPanEnabled = true

    private var maxScale = 5f

    private fun enableEditing() {
        matrix.set(imageMatrix)
        val drawable = drawable
        shownImageSize[0] = 0F
        shownImageSize[1] = 0F
        if (drawable != null) {
            imgW = drawable.intrinsicWidth
            imgH = drawable.intrinsicHeight
            shownImageSize[0] = imgW.toFloat()
            shownImageSize[1] = imgH.toFloat()
        }
        calcShownImageSize()
        if (!isInitialSizeSaved) {
            initialImageSize[0] = shownImageSize[0]
            initialImageSize[1] = shownImageSize[1]
            if (initialImageSize[0]<initialImageSize[1]) {
                val ratio = (measuredHeight.toFloat() * 0.2f) / initialImageSize[1]
                corners[0] = (measuredWidth.toFloat() - initialImageSize[0] * ratio) / 2f
                corners[1] = (measuredHeight.toFloat() - measuredHeight * 0.2f) / 2f
                corners[2] = measuredWidth.toFloat() - corners[0]
                corners[3] = measuredHeight.toFloat() - corners[1]
            } else {
                val ratio = (measuredWidth.toFloat() * 0.2f) / initialImageSize[0]
                corners[1] = (measuredHeight.toFloat() - initialImageSize[1] * ratio) / 2f
                corners[0] = (measuredWidth.toFloat() - measuredWidth.toFloat() * 0.2f) / 2f
                corners[3] = measuredHeight.toFloat() - corners[1]
                corners[2] = measuredWidth.toFloat() - corners[0]
            }
            isInitialSizeSaved = true
        }
    }

//    private fun setBackgroundDrawable() {
//        val bitmap = (drawable as BitmapDrawable).bitmap
//        val topHeight = bitmap.height * 0.1f
//        val topBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, topHeight.toInt())
//        val topPalette = Palette
//    }

    private fun calcShownImageSize() {
        matrix.getValues(matrixValue)
        shownImageSize[0] = imgW.toFloat()
        shownImageSize[1] = imgH.toFloat()
        val tmpW = shownImageSize[0]
        val tmpH = shownImageSize[1]
        shownImageSize[0] = matrixValue[0] * tmpW + matrixValue[1] * tmpH
        shownImageSize[1] = matrixValue[3] * tmpW + matrixValue[4] * tmpH
    }

    fun setZoomingEnabled(enabled: Boolean) {
        isZoomEnabled = enabled
    }

    fun setPanningEnabled(enabled: Boolean) {
        isPanEnabled = enabled
    }

    override fun setImageBitmap(bm: Bitmap) {
        super.setImageBitmap(bm)
        setImageProperty(bitmap = bm)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable is BitmapDrawable) {
            setImageProperty(drawable.bitmap)
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        val bitmapDrawable = ResourcesCompat.getDrawable(resources, resId, null) as BitmapDrawable
        setImageProperty(bitmapDrawable.bitmap)
    }

    private fun setImageProperty(bitmap: Bitmap) {
        imgW = bitmap.width
        imgH = bitmap.height
        scaleType = ScaleType.CENTER_CROP
        isInitialSizeSaved = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val act = event.action
        when (act and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                enableEditing()
                scaleType = ScaleType.MATRIX
                savedMatrix.set(matrix)
                start[event.x] = event.y
                matrix.getValues(matrixValue)
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10F) {
                    savedMatrix.set(matrix)
                    setMidPoint(mid, event)
                    mode = ZOOM
                }
            }
            MotionEvent.ACTION_MOVE -> {
                handleActionMove(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                enableEditing()
            }
        }
        imageMatrix = matrix
        return true
    }

    private fun handleActionMove(event: MotionEvent) {
        if (isPanEnabled && mode == DRAG) {
            handleDragModeActionMove(event)
        } else if (isZoomEnabled && mode == ZOOM) {
            val newDist = spacing(event)
            if (newDist>10f) {
                tmpMatrix.set(savedMatrix)
                val scale = newDist/oldDist
                if (checkForMaxScale(scale)) {
                    return
                }
                tmpMatrix.postScale(scale, scale, mid.x, mid.y)
                if (!checkForMinScale()) {
                    return
                }
                matrix.set(tmpMatrix)
                calcShownImageSize()
            }
        }
    }

    private fun checkForMinScale(): Boolean {
        val points = floatArrayOf(
            0f, 0f,
            imgW.toFloat(), 0f,
            imgW.toFloat(), imgH.toFloat(),
            0f, imgH.toFloat()
        )
        tmpMatrix.mapPoints(points)
        if (checkCorners(points)) {
            return false
        }

        if (points[0]>corners[0]) {
            tmpMatrix.postTranslate(corners[0] - points[0], 0F)
        }
        if (points[1]>corners[1]) {
            tmpMatrix.postTranslate(0F, corners[1] - points[1])
        }
        if (points[5]<corners[3]) {
            tmpMatrix.postTranslate(0F, (corners[3] - points[5]))
        }
        if (points[2]<corners[2]) {
            tmpMatrix.postTranslate(corners[2] - points[2], 0F)
        }
        val npoints = floatArrayOf(
            0f, 0f,
            imgW.toFloat(), 0f,
            imgW.toFloat(), imgH.toFloat(),
            0f, imgH.toFloat()
        )
        tmpMatrix.mapPoints(npoints)
        return !checkCorners(npoints)
    }

    private fun checkCorners(points: FloatArray): Boolean {
        return (points[0]>corners[0] && points[2]<corners[2]) || (points[1]>corners[1] && points[5]<corners[3]) ||
                (points[0].toInt()==corners[0].toInt() && points[2]<corners[2]) || (points[1].toInt()==corners[1].toInt() && points[5]<corners[3]) ||
                (points[0]>corners[0] && points[2].toInt()==corners[2].toInt()) || (points[1]>corners[1] && points[5].toInt()==corners[3].toInt())
    }

    private fun checkForMaxScale(scale: Float): Boolean {
        val ratioWidth = shownImageSize[0] / initialImageSize[0]
        val ratioHeight = shownImageSize[1] / initialImageSize[1]
        return scale>1f && (ratioHeight>maxScale || ratioWidth>maxScale)
    }

    private fun handleDragModeActionMove(event: MotionEvent) {
        matrix.set(savedMatrix)
        val destinyXPos = event.x - start.x
        val destinyYPos = event.y - start.y
        if (abs(destinyXPos)!=0f || abs(destinyYPos)!=0f) {
            matrix.postTranslate(destinyXPos, destinyYPos)
        }
    }

    private fun setMidPoint(mid: PointF, event: MotionEvent) {
        val xPoint = event.getX(0) + event.getX(1)
        val yPoint = event.getY(0) + event.getY(1)
        mid[xPoint/2] = yPoint/2
    }

    private fun spacing(event: MotionEvent) : Float {
        if (event.pointerCount >= 2) {
            val xPoint = event.getX(0) - event.getX(1)
            val yPoint = event.getY(0) - event.getY(1)
            return sqrt((xPoint * xPoint + yPoint * yPoint).toDouble()).toFloat()
        }
        return 0F
    }



    companion object {
        private const val TAG = "ZoomImageView"

        const val NONE = 0

        const val DRAG = 1

        const val ZOOM = 2
    }

}