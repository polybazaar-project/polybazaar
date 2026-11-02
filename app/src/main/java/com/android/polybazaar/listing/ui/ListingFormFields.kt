// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors

@Composable
internal fun FieldError(text: String, tag: String) {
  Text(
      text = text,
      color = AuthColors.Danger,
      fontSize = 12.sp,
      modifier = Modifier.testTag(tag),
  )
}

@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text.uppercase(),
      color = AuthColors.Accent,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = modifier,
  )
}

@Composable
internal fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    labelTag: String? = null,
    counter: String? = null,
    counterTag: String? = null,
    content: @Composable () -> Unit,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      FieldLabel(
          text = label,
          modifier = (if (labelTag != null) Modifier.testTag(labelTag) else Modifier).weight(1f),
      )
      counter?.let {
        Text(
            text = it,
            color = AuthColors.TextSecondary,
            fontSize = 11.sp,
            modifier = if (counterTag != null) Modifier.testTag(counterTag) else Modifier,
        )
      }
    }
    content()
  }
}

@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    onFocusLost: () -> Unit = {},
) {
  val textStyle = TextStyle(fontSize = 13.sp)
  // onFocusChanged also fires when the field is first composed, so only react to losing focus.
  var wasFocused by remember { mutableStateOf(false) }
  BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = singleLine,
      textStyle = textStyle.copy(color = AuthColors.TextPrimary),
      cursorBrush = SolidColor(AuthColors.Accent),
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      modifier =
          Modifier.fillMaxWidth().testTag(tag).onFocusChanged { state ->
            if (wasFocused && !state.isFocused) onFocusLost()
            wasFocused = state.isFocused
          },
      decorationBox = { innerTextField ->
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(AuthColors.Background, RoundedCornerShape(14.dp))
                    .border(1.dp, AuthColors.Outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          prefix?.let {
            Text(
                text = it,
                style =
                    textStyle.copy(
                        color = AuthColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
          }
          Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
              Text(text = placeholder, style = textStyle.copy(color = AuthColors.TextSecondary))
            }
            innerTextField()
          }
        }
      },
  )
}

@Composable
internal fun CategoryDropdown(
    selected: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(AuthColors.Background, RoundedCornerShape(14.dp))
                .border(1.dp, AuthColors.Outline, RoundedCornerShape(14.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(CreateListingScreenTags.CATEGORY_FIELD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = selected ?: "Choose category",
          color = if (selected == null) AuthColors.TextSecondary else AuthColors.TextPrimary,
          fontSize = 13.sp,
          maxLines = 1,
          modifier = Modifier.weight(1f),
      )
      Icon(
          painter = painterResource(R.drawable.ic_chevron_down),
          contentDescription = null,
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(16.dp),
      )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        containerColor = AuthColors.CardBackground,
    ) {
      options.forEach { option ->
        DropdownMenuItem(
            text = { Text(text = option, color = AuthColors.TextPrimary, fontSize = 13.sp) },
            onClick = {
              onSelect(option)
              expanded = false
            },
            modifier = Modifier.testTag(CreateListingScreenTags.categoryOption(option)),
        )
      }
    }
  }
}
