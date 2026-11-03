package space.nbtca.app.ui.screens

import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jsoup.nodes.Element

@Composable
private fun VideoWord(word: String, modifier: Modifier, animate: Boolean) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val running=animate && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    if (animate) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { VideoWordView(it) },
            modifier = modifier,
            update = { it.update(word.ifBlank { "NBTCA" }.uppercase(), running) },
            onRelease = { it.release() }
        )
        return
    }
    Canvas(modifier) {
        val text=word.ifBlank { "NBTCA" }.uppercase()
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface=android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD)
            textSize=size.height*.83f
            if(measureText(text)>size.width) textSize*=size.width/measureText(text)
        }
        paint.shader=android.graphics.LinearGradient(0f,0f,size.width,size.height,intArrayOf(0xFF41D9E9.toInt(),0xFF8A61D8.toInt(),0xFFEC95BE.toInt()),null,Shader.TileMode.CLAMP)
        drawContext.canvas.nativeCanvas.drawText(text,(size.width-paint.measureText(text))/2,(size.height-paint.ascent()-paint.descent())/2,paint)
    }
}

@Composable
internal fun TicketVisual(element: Element?, modifier: Modifier=Modifier, preview: Boolean=false, compact: Boolean=false) {
    val state=element?.attr("data-state").orEmpty()
    val code=element?.selectFirst("[data-ticket][data-check-in=open] svg.qr")
    var reveal by remember(element) { mutableStateOf(false) }
    val shape=remember { GenericShape { size,_ ->
        val base=Path().apply {addRoundRect(RoundRect(Rect(Offset.Zero,size),CornerRadius(size.width*.055f)))}
        val cuts=Path().apply {
            addOval(Rect(-size.width*.035f,size.height*.76f-size.width*.035f,size.width*.035f,size.height*.76f+size.width*.035f))
            addOval(Rect(size.width-size.width*.035f,size.height*.76f-size.width*.035f,size.width+size.width*.035f,size.height*.76f+size.width*.035f))
            addRoundRect(RoundRect(Rect(size.width*.4f,size.height*.035f,size.width*.6f,size.height*.058f),CornerRadius(size.width*.025f)))
        }
        addPath(Path.combine(PathOperation.Difference,base,cuts))
    } }
    BoxWithConstraints(modifier.aspectRatio(5f/8f).shadow(12.dp,shape).clip(shape).background(Color(0xFFFCFCFA))) {
        val w=maxWidth.value
        // Android shadow outlines may fill interior cutouts; keep the slot visibly recessed.
        Box(Modifier.align(Alignment.TopCenter).padding(top=(w*.055f).dp).width((w*.20f).dp).height((w*.036f).dp)
            .clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(Color(0xFF999C9F),Color(0xFFD4D6D8)))))
        Column(Modifier.fillMaxSize().padding(horizontal=(w*.075f).dp)) {
            Spacer(Modifier.height((w*.15f).dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("NBTCA EVENT PASS",fontSize=(w*.027f).sp,color=Color(0xFF17232B),fontWeight=FontWeight.Bold)
                Text(if(preview) "PREVIEW" else element?.selectFirst("[data-bind=date]")?.text().orEmpty(),fontSize=(w*.025f).sp,color=Color.Gray)
            }
            VideoWord(element?.selectFirst("[data-bind=word]")?.text() ?: "CARD",Modifier.fillMaxWidth().height((w*.35f).dp),!compact)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(preview) "你的名字" else element?.selectFirst(".who .name")?.text().orEmpty(),fontSize=(w*.065f).sp,color=Color(0xFF17232B),fontWeight=FontWeight.Bold,maxLines=1)
                    Text(if(preview) "活动通行证 · 样式预览" else element?.selectFirst("[data-bind=title]")?.text().orEmpty(),fontSize=(w*.032f).sp,color=Color.Gray,maxLines=2)
                }
                Canvas(Modifier.size((w*.14f).dp)) {
                    val colors=listOf(Color.Cyan,Color(0xFFEEC4FC),Color(0xFFF1E5A9),Color.Cyan)
                    drawCircle(Brush.sweepGradient(colors),style=androidx.compose.ui.graphics.drawscope.Stroke(width=4.dp.toPx()))
                    drawCircle(Brush.sweepGradient(colors),radius=size.width*.35f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf("VENUE" to "venue","DATE" to "day","TIME" to "time").forEach { (label,key) -> Column(Modifier.weight(1f)) {
                    Text(label,fontSize=(w*.023f).sp,color=Color.Gray)
                    Text(if(preview) "—" else element?.selectFirst("[data-bind=$key]")?.text().orEmpty(),fontSize=(w*.03f).sp,color=Color(0xFF17232B),maxLines=2)
                } }
            }
            Spacer(Modifier.height((w*.06f).dp))
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {drawLine(Color.LightGray,Offset.Zero,Offset(size.width,0f),pathEffect=PathEffect.dashPathEffect(floatArrayOf(7f,6f)))}
            Box(Modifier.fillMaxWidth().height((w*.34f).dp),contentAlignment=Alignment.Center) {
                when {
                    preview || state=="preview" -> Column(horizontalAlignment=Alignment.CenterHorizontally) {Text("仅供预览 · 非入场凭证",fontSize=(w*.037f).sp,color=Color.Gray); Text("领取后生成专属活动券",fontSize=(w*.028f).sp,color=Color.Gray)}
                    state=="redeemed" -> Text("已入场",fontWeight=FontWeight.Bold,color=Color(0xFF368C74),fontSize=(w*.08f).sp)
                    state=="expired" -> Text("活动已结束",color=Color.Gray)
                    code!=null && reveal -> Row(verticalAlignment=Alignment.CenterVertically) {
                        val svg=code.clone().attr("xmlns","http://www.w3.org/2000/svg").attr("width","256").attr("height","256")
                        coil.compose.AsyncImage(svg.outerHtml().toByteArray(),"入场二维码",modifier=Modifier.size((w*.29f).dp).background(Color.White).padding(8.dp))
                        Text(element.selectFirst(".serial")?.text().orEmpty(),fontSize=(w*.03f).sp,color=Color(0xFF17232B),modifier=Modifier.padding(start=12.dp))
                    }
                    else -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(0xFFE2F4F6),Color(0xFFF0E5F8))))
                        .clickable(enabled=code!=null){reveal=true}.padding(12.dp),contentAlignment=Alignment.Center) {
                        Text(if(code!=null) "点击揭开 · 出示入场码" else "活动开始前一小时开放入场码",fontSize=(w*.035f).sp,color=Color(0xFF344352))
                    }
                }
            }
        }
    }
}
