package space.nbtca.app.ui.screens

import android.webkit.CookieManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import space.nbtca.app.R
import space.nbtca.app.core.card.CardClient
import space.nbtca.app.core.card.MemberCardData

@Composable
internal fun rememberCardImageLoader(): ImageLoader {
    val context = LocalContext.current
    val loader = remember(context) { space.nbtca.app.core.card.CardImages.loader(context) }
    return loader
}

@Composable
internal fun CardImage(model: Any?, loader: ImageLoader, modifier: Modifier, scale: ContentScale = ContentScale.Crop, description: String? = null) {
    if (model == null) return
    val context = LocalContext.current
    val cookie = CookieManager.getInstance().getCookie(CardClient.ORIGIN).orEmpty()
    val request = remember(model,cookie) { space.nbtca.app.core.card.CardImages.request(context,model,cookie) }
    Box(modifier) {
        if(model is String && model.startsWith("/media/members/") && !model.contains(".thumb.")) {
            val thumb = remember(model,cookie) { space.nbtca.app.core.card.CardImages.request(context,space.nbtca.app.core.card.CardImages.thumbnail(model),cookie) }
            AsyncImage(model=thumb,imageLoader=loader,contentDescription=null,contentScale=scale,modifier=Modifier.matchParentSize())
        }
        AsyncImage(model = request, imageLoader = loader, contentDescription = description, contentScale = scale, modifier = Modifier.matchParentSize())
    }
}

@Composable
internal fun MemberCardVisual(data: MemberCardData, loader: ImageLoader, modifier: Modifier = Modifier,
    compact: Boolean = false, onClick: (() -> Unit)? = null, backgroundPreview: Any? = null, portraitPreview: Any? = null) {
    var flipped by remember(data.path, data.name) { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val flip by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = .82f, stiffness = 180f), label = "card flip")
    val rx by animateFloatAsState(drag.y * -9f, label = "card tilt x")
    val ry by animateFloatAsState(drag.x * 11f, label = "card tilt y")
    val tint = Color.hsv(data.hue.coerceIn(0f,360f), .72f, .65f)
    val context = LocalContext.current
    val logo = remember { context.assets.open("card/logo.svg").use { it.readBytes().toString(Charsets.UTF_8) }
        .replace(Regex("<style>.*?</style>"), "").replace("currentColor", "#ffffff").toByteArray() }
    BoxWithConstraints(modifier.aspectRatio(5f / 7f)) {
        val w = maxWidth.value
        val h = w * 1.4f
        val radius = (w * .07f).dp
        val gesture = if (compact) Modifier else Modifier.pointerInput(Unit) {
            detectDragGestures(onDragEnd = { drag = Offset.Zero }, onDragCancel = { drag = Offset.Zero }) { change, _ ->
                change.consume()
                drag = Offset((change.position.x / size.width * 2 - 1).coerceIn(-1f,1f), (change.position.y / size.height * 2 - 1).coerceIn(-1f,1f))
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { rotationX = rx; rotationY = flip + ry; cameraDistance = 16 * density }
            .shadow(if(compact) 5.dp else 16.dp, RoundedCornerShape(radius), ambientColor = tint, spotColor = tint)
            .clip(RoundedCornerShape(radius)).background(Brush.verticalGradient(listOf(tint, Color(0xFF071D32))))
            .then(gesture).clickable { if (onClick != null) onClick() else flipped = !flipped }) {
            if (flip <= 90f) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(tint.copy(alpha=.3f), Color.Transparent))))
                CardImage(backgroundPreview ?: data.background, loader, Modifier.fillMaxSize().graphicsLayer { translationX = drag.x * 4; translationY = -drag.y * 4; scaleX=1.05f; scaleY=1.05f })
                val word = data.word.ifBlank { data.alias }.uppercase()
                val units = word.sumOf { if(it.code > 255) 1.0 else .43 }.toFloat().coerceAtLeast(1f)
                Text(word, color = Color.White.copy(alpha=.94f), fontFamily = FontFamily(Font(R.font.card_display)),
                    fontSize = minOf(w * .44f, w * .88f / units).sp, maxLines=1, softWrap=false,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top=(h*.095f).dp).graphicsLayer { translationX=-drag.x*8; translationY=-drag.y*6 })
                if (portraitPreview != null || data.portrait != null) {
                    CardImage(portraitPreview ?: data.portrait, loader, Modifier.fillMaxSize().graphicsLayer { scaleX=1.08f; scaleY=1.08f; translationX=drag.x*12; translationY=-drag.y*8 })
                } else {
                    AsyncImage(logo, null, imageLoader=loader, modifier=Modifier.align(Alignment.BottomCenter).offset(y=(h*.08f).dp).fillMaxWidth(.92f).aspectRatio(1f))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .56f to Color.Transparent, 1f to tint.copy(alpha=.82f))))
                Row(Modifier.fillMaxWidth().padding(horizontal=(w*.06f).dp, vertical=(w*.05f).dp), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("NBTCA", color=Color.White, fontFamily=FontFamily(Font(R.font.card_display)), fontSize=(w*.047f).sp)
                    Text("SINCE ${data.joined}", color=Color.White, fontFamily=FontFamily(Font(R.font.card_display)), fontSize=(w*.047f).sp)
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=(w*.06f).dp, vertical=(w*.07f).dp), horizontalAlignment=Alignment.CenterHorizontally) {
                    HorizontalDivider(Modifier.width((w*.10f).dp).padding(bottom=(w*.035f).dp), color=Color.White.copy(alpha=.7f))
                    Text(data.name, color=Color.White, fontSize=(w*.076f).sp, fontWeight=FontWeight.SemiBold, textAlign=TextAlign.Center, maxLines=2, overflow=TextOverflow.Ellipsis)
                    Text(listOf(data.section, data.role).filter { it.isNotBlank() }.joinToString(" · "), color=Color.White.copy(alpha=.85f), fontSize=(w*.04f).sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                }
            } else {
                Column(Modifier.fillMaxSize().graphicsLayer { rotationY=180f }.padding((w*.08f).dp)) {
                    AsyncImage(logo, null, imageLoader=loader, modifier=Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(.56f).aspectRatio(1f))
                    Spacer(Modifier.weight(1f))
                    listOf(listOf("代号" to data.alias, "入会" to data.joined), listOf("部门" to data.section, "身份" to data.role)).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top=(w*.025f).dp)) { row.forEach { (label,value) -> Column(Modifier.weight(1f)) {
                            Text(label, color=Color.White.copy(alpha=.6f), fontSize=(w*.035f).sp)
                            Text(value.ifBlank { "—" }, color=Color.White, fontSize=(w*.048f).sp, fontWeight=FontWeight.Bold, maxLines=2)
                        } } }
                    }
                    if(data.github.isNotBlank()) { Text("GITHUB", color=Color.White.copy(alpha=.6f), fontSize=(w*.035f).sp, modifier=Modifier.padding(top=(w*.035f).dp)); Text("@${data.github}", color=Color.White, fontSize=(w*.045f).sp, maxLines=1, overflow=TextOverflow.Ellipsis) }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color=Color.White.copy(alpha=.2f))
                    Text(data.motto.ifBlank { "To be at the intersection of technology and liberal arts." }, color=Color.White, fontSize=(w*.04f).sp, modifier=Modifier.padding(vertical=(w*.04f).dp), maxLines=3)
                    Text("浙大宁波理工学院计算机协会", color=Color.White.copy(alpha=.6f), fontSize=(w*.032f).sp)
                }
            }
            // Static foil for thumbnails; only the touched detail card updates its highlights.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.Cyan.copy(alpha=.07f), Color.Magenta.copy(alpha=.05f), Color.Transparent),
                    start=Offset(size.width*(-.5f+drag.x*.3f),0f), end=Offset(size.width*1.5f,size.height)))
                if(drag != Offset.Zero) drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.20f),Color.Transparent), Offset(size.width*(.5f+drag.x*.4f),size.height*(.5f+drag.y*.4f)),size.width*.8f))
            }
        }
    }
}
