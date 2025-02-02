package cn.a10miaomiao.bilimiao.compose.pages.lyric

import android.app.Activity
import android.view.View
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import cn.a10miaomiao.bilimiao.compose.base.ComposePage
import cn.a10miaomiao.bilimiao.compose.comm.diViewModel
import cn.a10miaomiao.bilimiao.compose.comm.mypage.PageConfig
import cn.a10miaomiao.bilimiao.compose.comm.mypage.PageMenuItemClick
import cn.a10miaomiao.bilimiao.compose.pages.lyric.lib.KrcText
import cn.a10miaomiao.bilimiao.compose.pages.lyric.poup_menu.LyricOffsetPopupMenu
import cn.a10miaomiao.bilimiao.compose.pages.lyric.poup_menu.LyricSourcePopupMenu
import com.a10miaomiao.bilimiao.comm.mypage.MenuItemPropInfo
import com.a10miaomiao.bilimiao.comm.mypage.myMenuItem
import com.a10miaomiao.bilimiao.comm.network.MiaoHttp
import com.a10miaomiao.bilimiao.comm.store.PlayerStore
import com.a10miaomiao.bilimiao.store.WindowStore
import com.google.gson.Gson
import com.kongzue.dialogx.dialogs.PopTip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.compose.rememberInstance
import org.kodein.di.instance
import java.util.Base64

class LyricPage :ComposePage(){
    override val route: String
        get() = "lyric"

    @Composable
    override fun AnimatedContentScope.Content(navEntry: NavBackStackEntry) {
        val viewModel: LyricPageViewModel = diViewModel()
        LyricPageContent(viewModel)
    }
}

internal class LyricPageViewModel(
    override val di: DI,
): ViewModel(), DIAware{

    companion object{
        const val KUGOU = "ku"
        const val NETEASE = "net"
    }

    private val activity by instance<Activity>()
    var loadingSource = MutableStateFlow(false)
    var loadingLyric = MutableStateFlow(false)


    var lyricTitle= MutableStateFlow("")
    var author= MutableStateFlow("")
    var by= MutableStateFlow("")
    var lyric= MutableStateFlow(mutableStateListOf<LyricLine>())
    var offset= MutableStateFlow(0)

    var loadedSourceTitle=MutableStateFlow("\n")
    var source=MutableStateFlow(mutableStateListOf<LyricSource>())


    private val sourceMutex =Mutex()
    private val lyricMutex =Mutex()

    //加载过程中，一些提示信息放在歌词位置第一行
    fun setMessage(message:String){
        setLyric(mutableStateListOf(LyricLine(0,message)),"","","")
    }
    @Synchronized
    fun setLyric(list:MutableList<LyricLine>,title:String,author:String,by:String){
        this.lyricTitle.value=title
        this.author.value=author
        this.by.value=by
        this.lyric.value.clear()
        this.lyric.value.addAll(list)
    }
    @Synchronized
    fun addSource(list:MutableList<LyricSource>): Int{
        val originCount=source.value.count()
        source.value.addAll(list)
        // 返回值：新增项中的第一个序号
        return if(list.isEmpty()) -1 else originCount
    }
    fun loadSource(videoTitle:String) = viewModelScope.launch(Dispatchers.IO){
        sourceMutex.withLock {
            source.value.clear()
            loadedSourceTitle.value=videoTitle
            if(videoTitle==""){
                setMessage("当前无视频播放")
            } else {
                loadingSource.value = true
                setMessage("正在加载歌词源...")
                val res1=async{ loadSourceFromKugou(videoTitle) }
                val res2=async{ loadSourceFromNetease(videoTitle) }
                res1.await()
                res2.await()
                loadingSource.value = false
            }
        }
    }
    suspend fun loadSourceFromNetease(videoTitle: String){
        try {
            val res = MiaoHttp.request {
                url ="https://music.163.com/api/search/get/web?csrf_token=hlpretag=&hlposttag=&s=$videoTitle&type=1&offset=0&total=true&limit=12"
            }.awaitCall().let{
                val jsonStr = it.body!!.string()
                Gson().fromJson(jsonStr,NeteaseSearchResultInfo::class.java)
            }
            if (res.code==200) {
                val addList= mutableListOf<LyricSource>()
                res.result.songs.forEach {
                    addList.add(LyricSource(it.name,it.id, NETEASE,it.duration))
                }
                val shouldLoad = addSource(addList)
                loadLyric(shouldLoad)
            } else {
                withContext(Dispatchers.Main) {
                    PopTip.show("网易云歌词列表加载失败"+res.code.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                PopTip.show("网易云歌词列表加载失败")
            }
        }
    }

    suspend fun loadSourceFromKugou(videoTitle: String){
        try {
            val res = MiaoHttp.request {
                url ="https://mobileservice.kugou.com/api/v3/lyric/search?version=9108&highlight=1&keyword=$videoTitle&plat=0&pagesize=12&area_code=1&page=1&with_res_tag=1"
            }.awaitCall().let{
                val jsonStr = it.body!!.string().replace("<!--.*?-->".toRegex(),"")
                Gson().fromJson(jsonStr,KugouSearchResultInfo::class.java)
            }
            if (res.errcode == 0) {
                val addList= mutableListOf<LyricSource>()
                res.data.info.forEach {
                    addList.add(LyricSource(it.filename,it.hash, KUGOU,it.duration*100))
                }
                val shouldLoad = addSource(addList)
                loadLyric(shouldLoad)
            } else {
                withContext(Dispatchers.Main) {
                    PopTip.show("酷狗歌词列表加载失败"+res.error)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                PopTip.show("酷狗歌词列表加载失败")
            }
        }
    }
    fun String.decodeKrc():String{
        val base64DecodedBytes= Base64.getDecoder().decode(this)
        val byteArray = base64DecodedBytes.copyOfRange(4,base64DecodedBytes.size)
        return KrcText().getKrcText(byteArray)
    }

    suspend fun loadLyric(index:Int,replace:Boolean = false){
        lyricMutex.withLock {
            loadingLyric.value = true
            if(source.value.isEmpty()){
                setMessage("当前无歌词源")
                loadingLyric.value = false
                return
            }
            if(!replace && lyricTitle.value!=""){
                //已有歌词时默认不覆盖
                loadingLyric.value = false
                return
            }
            if(index !in source.value.indices){
                loadingLyric.value = false
                return
            }
            setMessage("正在加载歌词...")
            try {
                val src =source.value[index]
                var title=""
                var author=""
                var by=""
                val list= mutableListOf<LyricLine>()
                when (src.type) {
                    KUGOU -> {
                        val res1 = MiaoHttp.request {
                            url ="https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash=${src.code}&album_audio_id="
                        }.awaitCall().let{
                            val jsonStr=it.body!!.string()
                            Gson().fromJson(jsonStr,KugouAccessKeyItem::class.java)
                        }
                        if(res1.errcode==200){
                            val res2 = MiaoHttp.request {
                                val can = res1.candidates[0]
                                url ="https://lyrics.kugou.com/download?ver=1&client=pc&id=${can.id}&accesskey=${can.accesskey}&fmt=krc&charset=utf8"
                            }.awaitCall().let{
                                val jsonStr=it.body!!.string()
                                Gson().fromJson(jsonStr,KugouLyricItem::class.java)
                            }
                            if(res2.error_code==0){
                                res2.content.decodeKrc().split('\n').forEach {
                                    val full=it.substringBefore(']').substringAfter('[')
                                    val body=it.substringAfter(']')
                                    if(full.contains(':')){
                                        val left=full.substringBefore(':')
                                        val right=full.substringAfter(':')
                                        if(left=="ti"){
                                            title=right
                                        } else if(left=="by"){
                                            by=right
                                        } else if(left=="ar"){
                                            author=right
                                        } else if(left=="offset"){
                                            val time = right.toIntOrNull()
                                            if(time!=null&&time!=0){
                                                //offset为0时不覆盖原有的
                                                offset.value=time
                                            }
                                        }
                                    } else if(full.contains(',')){
                                        val time=full.substringBefore(',').toIntOrNull()
                                        if(time!=null){
                                            val regex="<.*?>".toRegex()
                                            val text=body.replace(regex,"")
                                            list.add(LyricLine(time.toLong(),text))
                                        }
                                    }
                                }
                            } else {
                                PopTip.show(res2.info)
                                list.add(LyricLine(0,"歌词详情获取失败"))
                            }
                        } else {
                            PopTip.show(res1.errmsg)
                            list.add(LyricLine(0,"歌词详情获取失败"))
                        }
                    }
                    NETEASE -> {
                        val res = MiaoHttp.request {
                            url ="https://music.163.com/api/song/media?id=${src.code}"
                        }.awaitCall().let{
                            val jsonStr=it.body!!.string()
                            Gson().fromJson(jsonStr,NeteaseLyricItem::class.java)
                        }
                        if (res.code==200) {
                            res.lyric.split('\n').forEach {
                                val left=it.substringBefore(':').substringAfter('[')
                                var right=it.substringBefore(']').substringAfter(':')
                                val body=it.substringAfter(']')
                                if(left=="title"){
                                    title=right
                                } else if(left=="by"){
                                    by=right
                                } else if(left=="author"){
                                    author=right
                                } else if(left=="offset"){
                                    val time = right.toIntOrNull()
                                    if(time!=null&&time!=0){
                                        //offset为0时不覆盖原有的
                                        offset.value=time
                                    }
                                } else {
                                    if(right.contains(':')){
                                        right = right.replace(':','.')
                                    }
                                    val minute=left.toIntOrNull()
                                    val second=right.toFloatOrNull()
                                    if(minute!=null&&second!=null){
                                        val time=minute*60000+(second*1000).toLong()
                                        list.add(LyricLine(time,body))
                                    }
                                }
                            }
                        } else {
                            PopTip.show("网易云歌词获取失败：${res.code}")
                            list.add(LyricLine(0,"歌词详情获取失败"))
                        }
                    }
                    else -> {
                        list.add(LyricLine(0,"歌词未知类型：${src.type}"))
                    }
                }
                if(list.isEmpty()){
                    list.add(LyricLine(0,"歌词内容为空"))
                }
                if(title==""){
                    //没读取到标题的，自动填充
                    title=src.name
                }
                setLyric(list, title, author, by)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    PopTip.show("歌词详情获取失败")
                    setMessage("歌词详情获取失败")
                }
            } finally {
                loadingLyric.value = false
            }
        }
    }

    fun menuItemClick(view: View, menuItem: MenuItemPropInfo){
        when (menuItem.key) {
            1 -> {
                if(source.value.isEmpty()){
                    PopTip.show("无歌词源")
                } else {
                    LyricSourcePopupMenu(activity,this).show(view)
                }
            }
            2 -> {
                LyricOffsetPopupMenu(activity,this).show(view)
            }
        }
    }
}
@Preview
@Composable
fun Preview(){
    val test1= LyricLine(1000,"第一行","first line")
    val test2= LyricLine(2000,"第二行","second line")
    val test3= LyricLine(3000,"第3行")
    val lyric = MutableStateFlow<List<LyricLine>>(listOf(test1,test2,test3)).collectAsState().value
    LazyColumn(){
        items(lyric){
            LyricItem(Color(0xffffffff),alpha = 1f, line = it)
        }
    }
}





@Composable
internal fun LyricPageContent(viewModel: LyricPageViewModel){

    val playerStore: PlayerStore by rememberInstance()
    val windowStore: WindowStore by rememberInstance()
    val playerState = playerStore.stateFlow.collectAsState().value
    val windowState = windowStore.stateFlow.collectAsState().value
    val lyric = viewModel.lyric.collectAsState().value
    val offset = viewModel.offset.collectAsState().value
    val title = viewModel.lyricTitle.collectAsState().value
    val focusOn = remember (playerState.playProgress,lyric,offset){
        derivedStateOf{
            var focused = -100
            for (item in lyric) {
                if (item.startTime + offset > playerStore.state.playProgress) {
                    focused = lyric.indexOf(item) - 1
                    break
                }
            }
            if(focused == -100){
                focused = lyric.size - 1
            }
            focused
        }.value
    }
    val scrollState= rememberLazyListState()
    val conf = LocalConfiguration.current
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    val spacerHeight = remember(canvasHeight,windowState.contentInsets.bottom){
        derivedStateOf {
            ((canvasHeight-windowState.contentInsets.bottom)/2*160/conf.densityDpi).toInt()-50
        }
    }
    LaunchedEffect(playerState.mainTitle){
        val videoTitle=playerState.mainTitle
            .replace("【.*?】".toRegex(),"")
            .replace("\\(.*?\\)".toRegex(),"") //过滤掉视频名中的括号，搜索更精准
            .substringBefore("feat.")
            .substringBeforeLast('/') //过滤掉作者信息
        if(videoTitle!=viewModel.loadedSourceTitle.value){
            viewModel.loadSource(videoTitle)
        }
    }
    LaunchedEffect(key1 = focusOn,spacerHeight){
        scrollState.animateScrollToItem(focusOn+1,-spacerHeight.value*conf.densityDpi/160)
    }
    Box{
        Canvas(modifier = Modifier.fillMaxSize()){
            canvasHeight = size.height
        }
        LazyColumn(state = scrollState) {
            item {
                Spacer(modifier = Modifier.height(spacerHeight.value.dp))
            }
            items(lyric) {
                if (focusOn == lyric.indexOf(it)) {
                    //当前播放
                    LyricItem(color = MaterialTheme.colorScheme.onSurface, alpha = 1f, line = it)
                } else {
                    //其他歌词
                    LyricItem(color = MaterialTheme.colorScheme.onSurface, alpha = 0.5f, line = it)
                }
            }
            item {
                Spacer(modifier = Modifier.height(spacerHeight.value.dp + windowState.contentInsets.bottomDp.dp))
            }
        }
    }
    PageConfig(
        title = "歌词-"+title.let {
            if(it==""){
                "暂无歌词"
            } else {
                if(it.length>15) {
                    it.take(13) + "..."
                }
                else {
                    it
                }
            }
        },
        menus = listOf(
            myMenuItem {
                key = 1
                this.title = "歌词源"
                iconFileName = "ic_more_vert_grey_24dp"
            },
            myMenuItem {
                key = 2
                this.title = if(offset==0) {
                    "延迟"
                }else if (offset>0) {
                    '+' + String.format("%.1f",offset/1000f) +'s'
                }else {
                    String.format("%.1f",offset/1000f) + 's'
                }
                iconFileName = "ic_history_gray_24dp"
            }
        )
    )
    PageMenuItemClick(viewModel::menuItemClick)
}

@Composable
internal fun LyricItem(color: Color, alpha: Float, line: LyricLine){
    Box(modifier = Modifier
        .height(100.dp)) {
        if (line.subText == null) {
            Text(
                text = line.mainText,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .alpha(alpha),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight(Alignment.CenterVertically)){
                Text(
                    line.mainText,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .alpha(alpha),
                )
                Text(
                    line.subText,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .alpha(alpha),
                )
            }
        }
    }
}

internal class LyricLine(
    val startTime:Long,
    val mainText:String,
    val subText:String?=null,
)

internal class LyricSource(
    val name:String,
    val code:String,
    val type:String,
    val duration: Int,
)