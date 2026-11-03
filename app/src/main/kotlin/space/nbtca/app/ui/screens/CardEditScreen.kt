package space.nbtca.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import space.nbtca.app.core.card.*
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardEditScreen(doc: Document, loader: coil.ImageLoader, onBack: () -> Unit, onSaved: (Document) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original=remember(doc) { MemberCardData.parse(doc.selectFirst(".card.full")!!) }
    val fields=remember { mutableStateMapOf<String,String>().apply {
        listOf("word","motto","section","role","joined","github","hue").forEach { put(it,doc.selectFirst("input[name=$it]")?.`val`().orEmpty()) }
    } }
    val initial=remember { fields.toMap() }
    var background by remember { mutableStateOf<Uri?>(null) }
    var portrait by remember { mutableStateOf<Uri?>(null) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }
    var cropKind by remember { mutableStateOf("background") }
    val cropFiles=remember { mutableListOf<java.io.File>() }
    DisposableEffect(Unit) { onDispose { cropFiles.forEach { it.delete() } } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val dirty = fields.toMap()!=initial || background!=null || portrait!=null
    fun back() { if(!busy) { if(dirty) discard=true else onBack() } }
    BackHandler { back() }
    val backgroundPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if(it!=null) {cropKind="background";cropSource=it} }
    val portraitPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if(it!=null) {cropKind="portrait";cropSource=it} }
    cropSource?.let { source ->
        CardCropScreen(source,cropKind,onCancel={cropSource=null},onCropped={uri ->
            cropFiles.add(java.io.File(requireNotNull(uri.path)))
            if(cropKind=="background") background=uri else portrait=uri
            cropSource=null
        })
        return
    }
    val preview=original.copy(word=fields["word"].orEmpty().ifBlank { original.alias },motto=fields["motto"].orEmpty(),
        section=fields["section"].orEmpty(), role=fields["role"].orEmpty(),joined=fields["joined"].orEmpty(),github=fields["github"].orEmpty(),hue=fields["hue"]?.toFloatOrNull() ?: original.hue)
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的修改？")},confirmButton={TextButton(onClick=onBack){Text("放弃")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
    Scaffold(topBar={TopAppBar(title={Text("装扮卡片")},navigationIcon={IconButton(onClick={back()},enabled=!busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=AppThemeColors.surface))},
        bottomBar={Surface(color=AppThemeColors.surface){Button(onClick={
            val year=fields["joined"]?.toIntOrNull()
            if(year==null || year !in 2000..java.time.LocalDate.now().year) {error="请输入有效的入会年份"; return@Button}
            busy=true; error=null
            scope.launch {
                try {
                    CardClient.page("/me",fields.toMap())
                    background?.let { CardMediaUpload.upload(context,"background",it) }
                    portrait?.let { CardMediaUpload.upload(context,"portrait",it) }
                    onSaved(CardClient.page("/me"))
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(failure:Exception) {error="保存未完成：${failure.message}。已成功提交的部分会保留，可重试。"}
                finally {busy=false}
            }
        },enabled=!busy,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),colors=ButtonDefaults.buttonColors(containerColor=AppColors.Primary)){Text(if(busy) "正在保存…" else "保存卡片")}}}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(bottom=16.dp)) {
            item { Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                MemberCardVisual(preview,loader,Modifier.width(230.dp),backgroundPreview=background,portraitPreview=portrait)
                Text("实时预览 · 点击翻面",Modifier.padding(top=16.dp),style=MaterialTheme.typography.bodySmall,color=AppThemeColors.textSecondary)
            } }
            error?.let { item {Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)} }
            item { FrostedCard {
                Text("图片",fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={backgroundPicker.launch("image/*")},enabled=!busy,modifier=Modifier.weight(1f)){Text("更换背景")}
                    OutlinedButton(onClick={portraitPicker.launch("image/*")},enabled=!busy,modifier=Modifier.weight(1f)){Text("更换人像")}
                }
                Text("背景推荐竖图；人像推荐透明 PNG。选择后先预览，保存时上传。",style=MaterialTheme.typography.bodySmall,color=AppThemeColors.textSecondary)
            } }
            item {FrostedCard {
                Text("主色调",fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf(212,248,280,320,350,18,42,140,172,196).forEach { hue ->
                        Box(Modifier.size(23.dp).background(Color.hsv(hue.toFloat(),.7f,.85f),CircleShape)
                            .border(if(preview.hue.toInt()==hue) 2.dp else 0.dp,AppColors.Primary,CircleShape)
                            .clickable(enabled=!busy){fields["hue"]=hue.toString()})
                    }
                }
                Slider(value=preview.hue.coerceIn(0f,360f),onValueChange={fields["hue"]=it.toInt().toString()},valueRange=0f..360f,enabled=!busy,
                    colors=SliderDefaults.colors(thumbColor=AppColors.Primary,activeTrackColor=AppColors.Primary))
            } }
            item {FrostedCard {
                Text("卡面资料",fontWeight=FontWeight.Bold)
                listOf(Triple("word","卡面大字",12),Triple("motto","个性签名",40),Triple("section","部门",12),Triple("role","身份",12),Triple("joined","入会年份",4),Triple("github","GitHub",39)).forEach { (key,label,max) ->
                    OutlinedTextField(fields[key].orEmpty(),{fields[key]=it.take(max)},label={Text(label)},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                }
            } }
        }
    }
}
