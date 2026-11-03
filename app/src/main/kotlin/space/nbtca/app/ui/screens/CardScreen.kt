package space.nbtca.app.ui.screens

import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import space.nbtca.app.core.card.MemberCardData
import space.nbtca.app.ui.theme.AppColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import space.nbtca.app.core.card.CardClient
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen() {
    var path by remember { mutableStateOf("/me") }
    var doc by remember { mutableStateOf<Document?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var login by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val imageLoader = rememberCardImageLoader()
    val config = LocalConfiguration.current
    val cardWidth = minOf(config.screenWidthDp - 64f, (config.screenHeightDp - 300f) / 1.4f).coerceAtLeast(180f).dp
    val scope = rememberCoroutineScope()
    fun navigate(next: String) { path = next; doc = null; error = null; notice = null }
    if (login) {
        CardLoginScreen(onClose = { login = false }, onLoggedIn = { login = false; refresh++ })
        return
    }
    LaunchedEffect(path, refresh) {
        loading = true; error = null; doc = null
        try { doc = CardClient.page(path) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { loading = false }
    }
    // A displayed pass is re-read so redeemed/expired codes are not kept on screen.
    LaunchedEffect(path, doc) {
        if (path.startsWith("/e/") && doc?.selectFirst("[data-ticket]") != null) {
            delay(15000)
            refresh++
        }
    }
    val detail = path.startsWith("/e/") || path.startsWith("/members/")
    BackHandler(detail) { navigate(if (path.startsWith("/members/")) "/members" else "/e") }
    fun post(fields: Map<String,String>) {
        if (submitting) return
        submitting = true
        scope.launch {
            try {
                doc = CardClient.page(path, fields)
                notice = if (path == "/me") "已保存" else "已更新活动券状态"
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message }
            finally { submitting = false }
        }
    }
    if (editing && doc?.selectFirst(".card.full") != null) {
        CardEditScreen(doc!!, imageLoader, onBack = { editing = false }, onSaved = { doc = it; editing = false; notice = "卡片已保存" })
        return
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("Card", fontWeight = FontWeight.Bold) }, navigationIcon = {
            if (detail) IconButton(onClick = { navigate(if (path.startsWith("/members/")) "/members" else "/e") }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        }, actions = {
            IconButton(onClick = { refresh++ }, enabled = !loading && !submitting) { Icon(Icons.Default.Refresh, "刷新 Card") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Card 菜单") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("重新授权") }, onClick = { menu = false; login = true })
                }
            }
        },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("/me" to "我的卡片", "/e" to "活动券", "/members" to "社员墙").forEach { (route,label) ->
                        FilterChip(selected = path == route, onClick = { if (!submitting) navigate(route) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.Primary.copy(alpha = .12f), selectedLabelColor = AppColors.Primary))
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            error?.let { message -> item { FrostedCard { Text(message); Row {
                TextButton(onClick = { refresh++ }) { Text("重试") }
                TextButton(onClick = { login = true }) { Text("登录 Card") }
            } } } }
            notice?.let { item { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.primary) } }
            val current = doc
            if (current != null) {
                if (path == "/e") {
                    val entries = current.select(".caption")
                    if (entries.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            TicketVisual(null, Modifier.width(cardWidth), preview = true)
                            Text("近期暂无活动", Modifier.padding(top = 20.dp), fontWeight = FontWeight.Bold)
                            Text("有新活动时，可在这里领取专属活动券", style = MaterialTheme.typography.bodySmall, color = AppThemeColors.textSecondary)
                        }
                    }
                    items(entries) { entry ->
                        val link = entry.selectFirst("a[href]")
                        FrostedCard(onClick = { link?.attr("href")?.let(::navigate) }) {
                            entry.parent()?.selectFirst(".pass-card")?.let { TicketVisual(it, Modifier.fillMaxWidth(), compact = true) }
                            Text(entry.selectFirst(".chip")?.text().orEmpty(), color = MaterialTheme.colorScheme.primary)
                            Text(entry.selectFirst("h3")?.text().orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp))
                            Text(entry.selectFirst("p")?.wholeText().orEmpty(), color = AppThemeColors.textSecondary)
                        }
                    }
                } else if (path == "/members") {
                    val cards = current.select("a.card")
                    if (cards.isEmpty()) item { FrostedCard { Text("暂无社员卡") } }
                    item { Text("${cards.size} 位社员", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = AppThemeColors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                    items(cards.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { card -> MemberCardVisual(MemberCardData.parse(card), imageLoader, Modifier.weight(1f), compact = true, onClick = { navigate(card.attr("href")) }) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else if (path == "/me" || path.startsWith("/members/")) {
                    current.selectFirst(".card.full")?.let { card -> item {
                        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            MemberCardVisual(MemberCardData.parse(card), imageLoader, Modifier.width(cardWidth))
                            Text("点击翻面 · 轻拖查看光泽", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodySmall, color = AppThemeColors.textSecondary)
                            if (path == "/me") Button(onClick = { editing = true }, modifier = Modifier.padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text("装扮我的卡片") }
                        }
                    } }
                } else if (path.startsWith("/e/")) {
                    current.selectFirst(".pass-card.full")?.let { pass -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { TicketVisual(pass, Modifier.width(cardWidth)) }
                    } }
                    item { FrostedCard {
                        Text(current.selectFirst(".info h1")?.text().orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(current.selectFirst(".info > .chip")?.text().orEmpty(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                        Text(current.selectFirst(".when")?.text().orEmpty())
                        current.selectFirst(".summary")?.text()?.let { Text(it, Modifier.padding(vertical = 12.dp)) }
                        current.select(".timeline li").forEach { Text(it.text(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                    } }
                    item { FrostedCard {
                        Text("我的活动券", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(current.selectFirst(".who .name")?.text().orEmpty())
                        Text(current.selectFirst(".act .notice")?.text().orEmpty(), modifier = Modifier.padding(vertical = 8.dp))
                        if (current.selectFirst("form.claim") != null) {
                            var holder by remember(current) { mutableStateOf(current.selectFirst("form.claim input[name=name]")?.`val`().orEmpty()) }
                            if (current.selectFirst("form.claim input[name=name]") != null) OutlinedTextField(holder, { holder = it.take(20) }, label = { Text("持券人姓名") })
                            Button(onClick = { post(mapOf("name" to holder)) }, enabled = !submitting) { Text(if (submitting) "正在领取…" else "领取活动券") }
                        } else if (current.selectFirst(".act a[href^=/login]") != null) {
                            Button(onClick = { login = true }) { Text("登录后领取") }
                        }
                    } }
                }
            }
        }
    }
}
