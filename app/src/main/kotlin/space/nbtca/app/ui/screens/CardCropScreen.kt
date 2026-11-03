package space.nbtca.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.nbtca.app.core.card.cropGeometry
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardCropScreen(source: Uri, kind: String, onCancel: () -> Unit, onCropped: (Uri) -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if(!busy) onCancel() }
    LaunchedEffect(source) {
        try {
            bitmap=withContext(Dispatchers.IO) {
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                context.contentResolver.openInputStream(source).use {BitmapFactory.decodeStream(it,null,bounds)}
                check(bounds.outWidth>0 && bounds.outHeight>0) {"无法读取图片"}
                val options=BitmapFactory.Options().apply {inSampleSize=1}
                while(maxOf(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2
                val decoded=context.contentResolver.openInputStream(source).use {BitmapFactory.decodeStream(it,null,options)} ?: error("图片解码失败")
                val orientation=runCatching {context.contentResolver.openInputStream(source).use {ExifInterface(it!!).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1)
                val matrix=Matrix().apply {
                    when(orientation) {
                        2 -> setScale(-1f,1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f,-1f)
                        5 -> {setRotate(90f);postScale(-1f,1f)}
                        6 -> setRotate(90f)
                        7 -> {setRotate(-90f);postScale(-1f,1f)}
                        8 -> setRotate(-90f)
                    }
                }
                if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also {if(it!==decoded) decoded.recycle()}
            }
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(failure:Exception) {error=failure.message ?: "无法读取图片"}
    }
    Scaffold(topBar={TopAppBar(title={Text(if(kind=="background") "裁切背景" else "裁切人像")},navigationIcon={IconButton(onClick=onCancel,enabled=!busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,"取消裁切")}})},
        bottomBar={Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("缩放")
                Slider(zoom,{zoom=it;pan=Offset.Zero},valueRange=1f..4f,enabled=bitmap!=null&&!busy,modifier=Modifier.weight(1f))
                TextButton(onClick={bitmap?.let {src -> bitmap=Bitmap.createBitmap(src,0,0,src.width,src.height,Matrix().apply {postRotate(90f)},true);zoom=1f;pan=Offset.Zero}},enabled=bitmap!=null&&!busy){Text("旋转")}
            }
            Button(onClick={
                val src=bitmap ?: return@Button
                if(viewport.width==0 || viewport.height==0) return@Button
                val rect=cropGeometry(src.width,src.height,viewport.width.toFloat(),viewport.height.toFloat(),zoom,pan.x,pan.y)
                busy=true;error=null
                scope.launch {
                    try {
                        val uri=withContext(Dispatchers.IO) {
                            val width=minOf(1000,rect.width.roundToInt()).coerceAtLeast(5)
                            val height=(width*1.4f).roundToInt()
                            val out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                            val transform=Matrix().apply {setScale(width/rect.width,height/rect.height);postTranslate(-rect.x*width/rect.width,-rect.y*height/rect.height)}
                            android.graphics.Canvas(out).drawBitmap(src,transform,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
                            val folder=File(context.cacheDir,"card-crops").apply {mkdirs()}
                            val file=File.createTempFile("crop-",".png",folder)
                            try {file.outputStream().use {check(out.compress(Bitmap.CompressFormat.PNG,100,it))};Uri.fromFile(file)}
                            catch(e:Exception) {file.delete();throw e}
                            finally {out.recycle()}
                        }
                        onCropped(uri)
                    } catch(cancelled:CancellationException) {throw cancelled}
                    catch(failure:Exception) {error="裁切失败：${failure.message}"}
                    finally {busy=false}
                }
            },enabled=bitmap!=null&&!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy) "正在处理…" else "使用裁切结果")}
        }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("拖动调整位置，双指缩放 · 5:7 卡片比例",style=MaterialTheme.typography.bodySmall)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                val width=minOf(maxWidth.value,maxHeight.value/1.4f).dp
                val src=bitmap
                if(src==null && error==null) CircularProgressIndicator()
                if(src!=null) Canvas(Modifier.width(width).aspectRatio(5f/7f).background(Color(0xFFCCCCCC))
                    .onSizeChanged {viewport=it}
                    .pointerInput(src,busy) {if(!busy) detectTransformGestures {_,delta,factor,_ ->
                        zoom=(zoom*factor).coerceIn(1f,4f)
                        val rect=cropGeometry(src.width,src.height,size.width.toFloat(),size.height.toFloat(),zoom,pan.x+delta.x,pan.y+delta.y)
                        pan=Offset(rect.left+(src.width*rect.scale-size.width)/2,rect.top+(src.height*rect.scale-size.height)/2)
                    }}) {
                    val rect=cropGeometry(src.width,src.height,size.width,size.height,zoom,pan.x,pan.y)
                    clipRect {
                        drawImage(src.asImageBitmap(),dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize((src.width*rect.scale).roundToInt(),(src.height*rect.scale).roundToInt()))
                        for(i in 1..2) {
                            drawLine(Color.White.copy(alpha=.7f),Offset(size.width*i/3,0f),Offset(size.width*i/3,size.height),1.dp.toPx())
                            drawLine(Color.White.copy(alpha=.7f),Offset(0f,size.height*i/3),Offset(size.width,size.height*i/3),1.dp.toPx())
                        }
                    }
                }
            }
            Text("确认后返回卡面预览，保存卡片时才上传",style=MaterialTheme.typography.bodySmall)
        }
    }
}
