package com.khabir.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RuledAgendaEditor(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFDF7))) {
        val pageHeight = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BasicTextField(
                value = value,
                onValueChange = { com.khabir.app.data.monetization.WorkAdEvents.interacted(); onValueChange(it) },
                cursorBrush = SolidColor(Color(0xFF80601F)),
                textStyle = TextStyle(color = Color(0xFF222222), fontSize = 18.sp, lineHeight = 34.sp, textDirection = TextDirection.ContentOrRtl),
                modifier = Modifier.fillMaxWidth().heightIn(min = pageHeight).padding(horizontal = 18.dp, vertical = 12.dp)
                    .testTag("agenda-ruled-editor")
                    .drawBehind {
                        val spacing = 34.sp.toPx()
                        var y = spacing
                        while (y < size.height) {
                            drawLine(Color(0xFFDADFE3), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            y += spacing
                        }
                    },
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty()) Text("اكتب ملاحظات اليوم…", color = Color(0xFF777777), fontSize = 18.sp, lineHeight = 34.sp)
                        field()
                    }
                }
            )
        }
    }
}


@Composable
internal fun RuledAgendaPreview(value: String, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp))
        .background(Color(0xFFFFFDF7)).testTag("agenda-text-preview")
        .verticalScroll(rememberScrollState())) {
        Text(value.ifBlank { "اضغط للكتابة في صفحة كاملة" },
            style = TextStyle(color = Color(0xFF222222), fontSize = 18.sp, lineHeight = 34.sp, textDirection = TextDirection.ContentOrRtl),
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                .clickable(onClickLabel = "فتح صفحة الكتابة", onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp).drawBehind {
                    val spacing = 34.sp.toPx()
                    var y = spacing
                    while (y < size.height) {
                        drawLine(Color(0xFFDADFE3), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        y += spacing
                    }
                })
    }
}
