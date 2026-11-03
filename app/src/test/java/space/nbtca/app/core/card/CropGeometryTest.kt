package space.nbtca.app.core.card
import org.junit.Test
import org.junit.Assert.*
class CropGeometryTest {
    @Test fun keepsCropInsideImageForAllEdges() {
        for((w,h) in listOf(3000 to 1000,1000 to 3000,1000 to 1000)) {
            for(zoom in listOf(1f,2f,4f)) for(x in listOf(-10000f,0f,10000f)) for(y in listOf(-10000f,0f,10000f)) {
                val r=cropGeometry(w,h,300f,420f,zoom,x,y)
                assertTrue(r.x>=-.01f && r.y>=-.01f)
                assertTrue(r.x+r.width<=w+.01f && r.y+r.height<=h+.01f)
                assertEquals(5f/7f,r.width/r.height,.0001f)
            }
        }
    }
    @Test fun centersLandscapeAndLimitsZoom() {
        val r=cropGeometry(1400,1000,300f,420f,0f,0f,0f)
        assertEquals(0f,r.y,.001f)
        assertEquals((1400-r.width)/2,r.x,.001f)
        assertEquals(cropGeometry(1400,1000,300f,420f,4f,0f,0f),cropGeometry(1400,1000,300f,420f,50f,0f,0f))
    }
}
