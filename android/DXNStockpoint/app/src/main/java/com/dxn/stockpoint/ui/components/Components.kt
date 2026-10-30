package com.dxn.stockpoint.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxn.stockpoint.data.model.Product
import com.dxn.stockpoint.ui.theme.*

// ── Glassmorphic Product Card ─────────────────────────────────────────────────

@Composable
fun GlassmorphicProductCard(
    product: Product,
    onAddToCart: () -> Unit
) {
    val stockColor = if (product.stockQty < 5) Color(0xFFD32F2F) else EmeraldGreen
    val isOutOfStock = product.stockQty == 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Top row: category + stock badge + PV badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.category.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Stock badge
                    Text(
                        text = if (isOutOfStock) "OUT OF STOCK" else "Stock: ${product.stockQty}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(stockColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    // PV badge
                    Text(
                        text = "PV: ${product.pvPoints}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ForestGreen,
                        modifier = Modifier
                            .background(LightGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Member: ৳${"%.2f".format(product.memberPrice)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ForestGreen
                    )
                    Text(
                        text = "Retail: ৳${"%.2f".format(product.retailPrice)}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textDecoration = TextDecoration.LineThrough
                    )
                }

                Button(
                    onClick = onAddToCart,
                    enabled = !isOutOfStock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        disabledContainerColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isOutOfStock) "Sold Out" else "Add", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

// ── Cart FAB with Badge ───────────────────────────────────────────────────────

@Composable
fun CartBadgeFab(itemCount: Int, onClick: () -> Unit) {
    Box {
        FloatingActionButton(
            onClick = onClick,
            containerColor = ForestGreen,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
        }
        if (itemCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .background(Color(0xFFD32F2F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (itemCount > 99) "99+" else itemCount.toString(),
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LightGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = ForestGreen)
            Text(text = title, fontSize = 11.sp, color = DarkText.copy(alpha = 0.7f))
        }
    }
}

// ── Loading Skeleton ──────────────────────────────────────────────────────────

@Composable
fun ProductCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.LightGray.copy(alpha = alpha))
    )
}

