package chat.bitchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.domain.router.MeshGraph
import chat.bitchat.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun MeshVisualizer(
    graph: MeshGraph,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (graph.nodes.size <= 1) {
            ScanningState(label = "Mapping nearby mesh nodes", isScanning = true)
            return@Box
        }
        
        val successColor = EchoSuccess
        val accentColor = EchoAccent
        val hairlineColor = EchoHairline
        val textPrimaryColor = EchoTextPrimary

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.6f

            // 1. Assign positions to nodes
            val nodePositions = mutableMapOf<String, Offset>()
            val otherNodes = graph.nodes.filter { !it.isLocal }
            val localNode = graph.nodes.find { it.isLocal }
            
            if (localNode != null) {
                nodePositions[localNode.id] = center
            }

            if (otherNodes.isNotEmpty()) {
                val angleStep = (2 * Math.PI) / otherNodes.size
                otherNodes.forEachIndexed { index, node ->
                    val angle = index * angleStep
                    val x = center.x + radius * cos(angle).toFloat()
                    val y = center.y + radius * sin(angle).toFloat()
                    nodePositions[node.id] = Offset(x, y)
                }
            }

            // 2. Draw clean connection edges
            graph.edges.forEach { edge ->
                val fromPos = nodePositions[edge.from]
                val toPos = nodePositions[edge.to]
                if (fromPos != null && toPos != null) {
                    val rssi = edge.rssi.toInt()
                    val edgeColor = if (rssi > -70) successColor else accentColor
                    
                    drawLine(
                        color = edgeColor.copy(alpha = 0.55f),
                        start = fromPos,
                        end = toPos,
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Draw nodes
            graph.nodes.forEach { node ->
                val pos = nodePositions[node.id] ?: return@forEach
                val isMe = node.isLocal
                val nodeColor = if (isMe) accentColor else successColor
                
                // Outer ring
                drawCircle(
                    color = nodeColor.copy(alpha = 0.2f),
                    radius = 12.dp.toPx(),
                    center = pos
                )
                // Center core
                drawCircle(
                    color = nodeColor,
                    radius = 5.dp.toPx(),
                    center = pos
                )
                
                // Label
                val textLayoutResult = textMeasurer.measure(
                    text = if (isMe) "You" else node.name,
                    style = TextStyle(
                        color = textPrimaryColor,
                        fontSize = 11.sp,
                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = pos.x - (textLayoutResult.size.width / 2f),
                        y = pos.y + 12.dp.toPx()
                    )
                )
            }
        }
    }
}
