// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R

/** Colors shared by the "Sign in" and "Create account" Figma frames. */
internal object AuthColors {
  val Background = Color(0xFFFFF7F2)
  val CardBackground = Color(0xFFFFFDF9)
  val Outline = Color(0xFFF1E1D5)
  val Accent = Color(0xFFC86A3B)
  val TextPrimary = Color(0xFF20231F)
  val TextSecondary = Color(0xFF70756D)
  val Danger = Color(0xFFA63D2B)
}

/** The "ACCOUNT" eyebrow above a screen title. */
@Composable
internal fun AuthTitleGroup(title: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = "ACCOUNT",
        color = AuthColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = title,
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
internal fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
        text = label.uppercase(),
        color = AuthColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle.copy(color = AuthColors.TextPrimary),
        cursorBrush = SolidColor(AuthColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        decorationBox = { innerTextField ->
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .background(AuthColors.Background, RoundedCornerShape(14.dp))
                      .border(1.dp, AuthColors.Outline, RoundedCornerShape(14.dp))
                      .padding(horizontal = 14.dp, vertical = 12.dp)
          ) {
            if (value.isEmpty()) {
              Text(text = placeholder, style = textStyle.copy(color = AuthColors.TextSecondary))
            }
            innerTextField()
          }
        },
    )
  }
}

/** The house icon and tagline at the bottom of the auth screens. */
@Composable
internal fun CommunityReassurance(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_house),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text = "Good tools. Great neighbours.",
        color = AuthColors.TextSecondary,
        fontSize = 12.sp,
        lineHeight = 1.4.em,
        textAlign = TextAlign.Center,
    )
  }
}
