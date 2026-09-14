package chat.bitchat.domain.router

import android.util.Log
import chat.bitchat.core.bluetooth.BLEMeshManager
import chat.bitchat.data.database.EchoMeshDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

data class MeshNode(
    val id: String,
    val name: String,
    val lastSeen: Long,
    val isLocal: Boolean = false
)

data class MeshEdge(
    val from: String,
    val to: String,
    val rssi: Byte,
    val lastSeen: Long
)

data class MeshGraph(
    val nodes: List<MeshNode> = emptyList(),
    val edges: List<MeshEdge> = emptyList()
)

@Singleton
class MeshTopologyRepository @Inject constructor(
    private val bleMeshManager: BLEMeshManager,
    private val database: EchoMeshDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _graph = MutableStateFlow(MeshGraph())
    val graph: StateFlow<MeshGraph> = _graph.asStateFlow()
    
    private val nodeCache = ConcurrentHashMap<String, MeshNode>()
    private val edgeCache = ConcurrentHashMap<String, MeshEdge>()

    init {
        observePackets()
        startPruningLoop()
    }

    private fun observePackets() {
        scope.launch {
            bleMeshManager.receivedPackets.collect { (senderAddress, packet) ->
                val now = System.currentTimeMillis()
                
                // Add the sender and local node even if no route (direct 1-hop message)
                val originalSender = packet.senderID.toHexString()
                val localId = getMockPeerID(bleMeshManager.localIdentity).toHexString()
                
                registerNode(originalSender, "Unknown", now)
                registerNode(localId, "You", now, isLocal = true)
                
                val route = packet.route
                val metrics = packet.routeMetrics
                
                if (route != null && metrics != null && route.size == metrics.size) {
                    var previousNode = originalSender
                    for (i in route.indices) {
                        val hopNode = route[i].toHexString()
                        val rssi = metrics[i]
                        
                        registerNode(hopNode, "Node", now)
                        registerEdge(previousNode, hopNode, rssi, now)
                        
                        previousNode = hopNode
                    }
                    val rssiToUs = bleMeshManager.getLinkQuality(senderAddress)
                    registerEdge(previousNode, localId, rssiToUs, now)
                } else {
                    // Direct connection
                    val rssiToUs = bleMeshManager.getLinkQuality(senderAddress)
                    registerEdge(originalSender, localId, rssiToUs, now)
                }
                
                updateGraph()
            }
        }
    }

    private suspend fun registerNode(id: String, defaultName: String, timestamp: Long, isLocal: Boolean = false) {
        var name = nodeCache[id]?.name
        if (name == null || name == "Node" || name == "Unknown") {
            name = if (defaultName != "Node" && defaultName != "Unknown") {
                defaultName
            } else {
                resolveName(id)
            }
        }
        nodeCache[id] = MeshNode(id, name, timestamp, isLocal)
    }

    private suspend fun resolveName(hashHex: String): String {
        val peers = database.peerDao().getAllPeersDirect()
        for (p in peers) {
            val peerHash = getMockPeerID(p.peerID).toHexString()
            if (peerHash == hashHex) {
                return p.nickname
            }
        }
        return "Node"
    }

    private fun registerEdge(from: String, to: String, rssi: Byte, timestamp: Long) {
        val key = if (from < to) "$from-$to" else "$to-$from"
        edgeCache[key] = MeshEdge(from, to, rssi, timestamp)
    }

    private fun updateGraph() {
        _graph.update { 
            MeshGraph(
                nodes = nodeCache.values.toList(),
                edges = edgeCache.values.toList()
            )
        }
    }

    private fun startPruningLoop() {
        scope.launch {
            while (true) {
                delay(5_000)
                val cutoff = System.currentTimeMillis() - 45_000 // 45 seconds live radar
                
                var changed = false
                
                val nodesIt = nodeCache.iterator()
                while (nodesIt.hasNext()) {
                    val node = nodesIt.next().value
                    if (node.lastSeen < cutoff && !node.isLocal) {
                        nodesIt.remove()
                        changed = true
                    }
                }
                
                val edgesIt = edgeCache.iterator()
                while (edgesIt.hasNext()) {
                    if (edgesIt.next().value.lastSeen < cutoff) {
                        edgesIt.remove()
                        changed = true
                    }
                }
                
                if (changed) updateGraph()
            }
        }
    }

    private fun getMockPeerID(name: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(name.toByteArray()).copyOf(8)
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
