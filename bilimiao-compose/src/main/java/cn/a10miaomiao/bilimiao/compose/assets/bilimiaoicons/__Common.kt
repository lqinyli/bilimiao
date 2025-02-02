package cn.a10miaomiao.bilimiao.compose.assets.bilimiaoicons

import androidx.compose.ui.graphics.vector.ImageVector
import cn.a10miaomiao.bilimiao.compose.assets.BilimiaoIcons
import cn.a10miaomiao.bilimiao.compose.assets.bilimiaoicons.common.Danmukunum
import cn.a10miaomiao.bilimiao.compose.assets.bilimiaoicons.common.Playnum
import cn.a10miaomiao.bilimiao.compose.assets.bilimiaoicons.common.Upper
import kotlin.collections.List as ____KtList

public object CommonGroup

public val BilimiaoIcons.Common: CommonGroup
  get() = CommonGroup

private var __AllIcons: ____KtList<ImageVector>? = null

public val CommonGroup.AllIcons: ____KtList<ImageVector>
  get() {
    if (__AllIcons != null) {
      return __AllIcons!!
    }
    __AllIcons= listOf(Danmukunum, Playnum, Upper)
    return __AllIcons!!
  }
