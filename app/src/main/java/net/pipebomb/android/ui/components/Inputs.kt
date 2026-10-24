package net.pipebomb.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun PipeBombTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    password: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    val colors = PipeBombTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = PipeBombTheme.typography.input,
                    color = colors.inputPlaceholder,
                )
            }
        },
        label = label?.let {
            {
                Text(
                    text = it,
                    style = PipeBombTheme.typography.metadata,
                    color = colors.fgTertiary,
                )
            }
        },
        textStyle = PipeBombTheme.typography.input,
        singleLine = singleLine,
        isError = isError,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = PipeBombTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.inputBg,
            unfocusedContainerColor = colors.inputBg,
            disabledContainerColor = colors.inputBg,
            focusedTextColor = colors.inputFg,
            unfocusedTextColor = colors.inputFg,
            disabledTextColor = colors.fgMuted,
            focusedBorderColor = colors.borderFocus,
            unfocusedBorderColor = colors.inputBorder,
            disabledBorderColor = colors.inputBorder,
            errorBorderColor = colors.error,
            cursorColor = colors.accentPrimary,
        ),
    )
}

@Composable
fun PipeBombCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PipeBombTheme.colors
    val shape = PipeBombTheme.shapes.small
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (checked) colors.accentPrimary else colors.inputBg, shape)
                .border(1.dp, if (enabled) colors.borderStrong else colors.fgMuted, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.size(14.dp)) {
                    val stroke = 2.5.dp.toPx()
                    drawLine(
                        color = colors.fgPrimary,
                        start = Offset(0f, size.height * 0.55f),
                        end = Offset(size.width * 0.4f, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = colors.fgPrimary,
                        start = Offset(size.width * 0.4f, size.height),
                        end = Offset(size.width, size.height * 0.15f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
fun PipeBombTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        options.forEachIndexed { index, label ->
            PipeBombButton(
                text = label,
                onClick = { onSelect(index) },
                variant = if (index == selectedIndex) {
                    PipeBombButtonVariant.Primary
                } else {
                    PipeBombButtonVariant.Secondary
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun InputsPreview() {
    PipeBombTheme {
        Box(Modifier.background(PipeBombTheme.colors.bgBase)) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.size(width = 360.dp, height = 220.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PipeBombTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Server URL",
                )
                PipeBombCheckbox(checked = true, onCheckedChange = {})
                PipeBombTabs(
                    options = listOf("Tracks", "Albums", "Artists"),
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }
    }
}
