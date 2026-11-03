package space.nbtca.app.core.card

import kotlin.math.max

data class CropGeometry(val scale: Float, val left: Float, val top: Float, val x: Float, val y: Float, val width: Float, val height: Float)
fun cropGeometry(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float, zoom: Float, panX: Float, panY: Float): CropGeometry {
    require(imageWidth>0 && imageHeight>0 && viewWidth>0 && viewHeight>0)
    val scale=max(viewWidth/imageWidth,viewHeight/imageHeight)*zoom.coerceIn(1f,4f)
    val limitX=(imageWidth*scale-viewWidth)/2
    val limitY=(imageHeight*scale-viewHeight)/2
    val left=-limitX+panX.coerceIn(-limitX,limitX)
    val top=-limitY+panY.coerceIn(-limitY,limitY)
    return CropGeometry(scale,left,top,-left/scale,-top/scale,viewWidth/scale,viewHeight/scale)
}
