// Host tests only; outside Gradle source sets. Uses actual image dimensions for DOCX fixtures.
package android.graphics

class Bitmap(val width:Int,val height:Int)
object BitmapFactory {
    fun decodeByteArray(bytes:ByteArray,offset:Int,length:Int):Bitmap? = runCatching {
        javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes,offset,length))?.let { Bitmap(it.width,it.height) }
    }.getOrNull()
}
