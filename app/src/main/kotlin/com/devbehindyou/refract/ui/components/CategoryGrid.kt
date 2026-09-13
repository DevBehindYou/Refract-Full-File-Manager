package com.devbehindyou.refract.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileCategory

data class CategoryItem(
    val category: FileCategory,
    val title: String,
    val icon: ImageVector,
    val tintColor: Color,
)

val defaultCategories =
    listOf(
        CategoryItem(FileCategory.IMAGE, "Images", Icons.Default.Image, Color(0xFFE57373)),
        CategoryItem(FileCategory.VIDEO, "Videos", Icons.Default.Videocam, Color(0xFFBA68C8)),
        CategoryItem(FileCategory.AUDIO, "Audio", Icons.Default.Audiotrack, Color(0xFF64B5F6)),
        CategoryItem(FileCategory.DOCUMENT, "Docs", Icons.Default.Description, Color(0xFF4DB6AC)),
        CategoryItem(FileCategory.DOWNLOAD, "Downloads", Icons.Default.Download, Color(0xFFFFB74D)),
        CategoryItem(FileCategory.ARCHIVE, "Archives", Icons.Default.FolderZip, Color(0xFFA1887F)),
        CategoryItem(FileCategory.APK, "Apps", Icons.Default.Android, Color(0xFF81C784)),
        CategoryItem(FileCategory.OTHER, "Other", Icons.Default.MoreHoriz, Color(0xFF90A4AE)),
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(
    onCategoryClick: (FileCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        FlowRow(
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            defaultCategories.forEach { item ->
                CategoryTile(
                    item = item,
                    onClick = { onCategoryClick(item.category) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun CategoryTile(
    item: CategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .aspectRatio(1f)
                .testTag("category_tile_${item.category.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        onClick = onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.tintColor,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
