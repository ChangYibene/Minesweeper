package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

// --- 1. 数据模型 ---
data class Cell(
    val id: Int,
    val row: Int,
    val col: Int,
    val isMine: Boolean = false,
    val number: Int = 0,
    val isRevealed: Boolean = false,
    val isFlagged: Boolean = false
)

// 新增：关卡配置数据类
data class LevelConfig(
    val name: String,
    val rows: Int,
    val cols: Int,
    val totalMines: Int
)

enum class GameState { NotStarted, Running, Won, Lost }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF5F5F5)
                ) {
                    MinesweeperGame()
                }
            }
        }
    }
}

// --- 2. 游戏主逻辑 ---
@Composable
fun MinesweeperGame() {
    // --- 定义关卡列表 ---
    val levels = listOf(
        LevelConfig("初级", 8, 8, 8),    // 简单：适合练手
        LevelConfig("中级", 10, 10, 15), // 经典：现在的难度
        LevelConfig("高级", 14, 10, 30)  // 困难：雷密度大，行数多
    )

    // 状态管理
    var currentLevelIndex by remember { mutableIntStateOf(0) } // 当前关卡索引
    val currentLevel = levels[currentLevelIndex] // 获取当前关卡配置

    var gameState by remember { mutableStateOf(GameState.NotStarted) }
    var board by remember { mutableStateOf<List<Cell>>(emptyList()) }
    var minesLeft by remember { mutableIntStateOf(currentLevel.totalMines) }
    var timeSeconds by remember { mutableLongStateOf(0L) }

    // 初始化/重置 (根据当前关卡配置)
    fun startNewGame() {
        gameState = GameState.NotStarted
        minesLeft = currentLevel.totalMines
        timeSeconds = 0
        board = List(currentLevel.rows * currentLevel.cols) { index ->
            Cell(id = index, row = index / currentLevel.cols, col = index % currentLevel.cols)
        }
    }

    // 切换到下一关
    fun nextLevel() {
        if (currentLevelIndex < levels.size - 1) {
            currentLevelIndex++
        } else {
            // 通关了回到第一关，或者你可以留在这里
            currentLevelIndex = 0
        }
        // startNewGame 会由上面的 currentLevel 变化触发吗？
        // 不会自动触发重置逻辑，所以需要手动调用
        // 但由于 startNewGame 依赖 currentLevel，我们需要用 LaunchedEffect 监听 level 变化
    }

    // 监听关卡变化，自动开始新游戏
    LaunchedEffect(currentLevelIndex) {
        startNewGame()
    }

    // 首次运行
    LaunchedEffect(Unit) { startNewGame() }

    // 计时器
    LaunchedEffect(gameState) {
        if (gameState == GameState.Running) {
            val startTime = System.currentTimeMillis()
            while (gameState == GameState.Running) {
                timeSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000L)
            }
        }
    }

    // 生成地雷
    fun generateBoard(safeCellId: Int): List<Cell> {
        val newBoard = board.toMutableList()
        val totalCells = currentLevel.rows * currentLevel.cols
        var minesPlaced = 0

        while (minesPlaced < currentLevel.totalMines) {
            val randomIdx = (0 until totalCells).random()
            if (randomIdx != safeCellId && !newBoard[randomIdx].isMine) {
                newBoard[randomIdx] = newBoard[randomIdx].copy(isMine = true)
                minesPlaced++
            }
        }

        // 计算数字
        for (i in 0 until totalCells) {
            if (!newBoard[i].isMine) {
                val r = i / currentLevel.cols
                val c = i % currentLevel.cols
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until currentLevel.rows && nc in 0 until currentLevel.cols) {
                            if (newBoard[nr * currentLevel.cols + nc].isMine) count++
                        }
                    }
                }
                newBoard[i] = newBoard[i].copy(number = count)
            }
        }
        return newBoard
    }

    // 递归翻开
    fun revealCellsRecursive(currentBoard: MutableList<Cell>, index: Int) {
        val cell = currentBoard[index]
        if (cell.isRevealed || cell.isFlagged) return

        currentBoard[index] = cell.copy(isRevealed = true)

        if (cell.number == 0) {
            val r = cell.row
            val c = cell.col
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until currentLevel.rows && nc in 0 until currentLevel.cols) {
                        revealCellsRecursive(currentBoard, nr * currentLevel.cols + nc)
                    }
                }
            }
        }
    }

    // 点击事件
    fun onCellClick(cell: Cell) {
        if (gameState == GameState.Won || gameState == GameState.Lost) return
        if (cell.isFlagged || cell.isRevealed) return

        var currentBoard = board.toMutableList()

        if (gameState == GameState.NotStarted) {
            gameState = GameState.Running
            currentBoard = generateBoard(cell.id).toMutableList()
        }

        if (currentBoard[cell.id].isMine) {
            gameState = GameState.Lost
            currentBoard.forEachIndexed { idx, c ->
                if (c.isMine) currentBoard[idx] = c.copy(isRevealed = true)
            }
        } else {
            revealCellsRecursive(currentBoard, cell.id)
            val revealedCount = currentBoard.count { it.isRevealed }
            if (revealedCount == (currentLevel.rows * currentLevel.cols) - currentLevel.totalMines) {
                gameState = GameState.Won
                minesLeft = 0
            }
        }
        board = currentBoard
    }

    // 长按插旗
    fun onCellLongPress(cell: Cell) {
        if (gameState == GameState.Won || gameState == GameState.Lost) return
        if (cell.isRevealed) return

        if (gameState == GameState.NotStarted) gameState = GameState.Running

        val newBoard = board.toMutableList()
        val isFlagged = !cell.isFlagged
        newBoard[cell.id] = cell.copy(isFlagged = isFlagged)

        if (isFlagged) minesLeft-- else minesLeft++
        board = newBoard
    }

    // --- UI 布局 ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 1. 顶部栏：显示难度和重置
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 点击文字可以切换难度（循环切换）
            Button(
                onClick = { nextLevel() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "难度: ${currentLevel.name}",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            // 重置按钮
            Button(onClick = { startNewGame() }) {
                Text("重置")
            }
        }

        // 2. 状态仪表盘
        Card(
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("剩余", fontSize = 12.sp, color = Color.Gray)
                    Text("💣 $minesLeft", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }

                // 表情状态
                Text(
                    text = when (gameState) {
                        GameState.Won -> "😎"
                        GameState.Lost -> "😵"
                        else -> "🙂"
                    },
                    fontSize = 32.sp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("时间", fontSize = 12.sp, color = Color.Gray)
                    Text("⏱ %03d".format(timeSeconds), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. 游戏结果提示 & 下一关按钮
        if (gameState == GameState.Won) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉 胜利！", color = Color(0xFF4CAF50), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (currentLevelIndex < levels.size - 1) {
                    Button(
                        onClick = { nextLevel() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("进入下一关 ➡️")
                    }
                } else {
                    Text("你已通关全部难度！🏆", color = Color.Gray)
                }
            }
        } else if (gameState == GameState.Lost) {
            Text("💥 游戏结束", color = Color(0xFFE53935), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else {
            // 占位，防止布局跳动
            Spacer(modifier = Modifier.height(32.dp))
        }

        // 4. 棋盘渲染
        val config = LocalConfiguration.current
        val screenWidth = config.screenWidthDp.dp
        // 动态计算格子大小：根据当前列数计算
        val cellSize = (screenWidth - 32.dp) / currentLevel.cols

        LazyVerticalGrid(
            columns = GridCells.Fixed(currentLevel.cols),
            modifier = Modifier
                .width(screenWidth - 32.dp)
                .weight(1f) // 使用 weight 避免超出屏幕，如果很高可以滚动
                .background(Color(0xFFBDBDBD), RoundedCornerShape(4.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(board) { cell ->
                MineCell(
                    cell = cell,
                    size = cellSize,
                    onClick = { onCellClick(cell) },
                    onLongClick = { onCellLongPress(cell) }
                )
            }
        }
    }
}

// --- 3. 单个格子组件 (Emoji 版) ---
@Composable
fun MineCell(
    cell: Cell,
    size: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    cell.isRevealed && cell.isMine -> Color(0xFFE53935)
                    cell.isRevealed -> Color(0xFFE0E0E0)
                    else -> Color(0xFF90A4AE)
                }
            )
            .pointerInput(cell.isRevealed) {
                if (!cell.isRevealed) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (cell.isRevealed) {
            if (cell.isMine) {
                Text("💣", fontSize = (size.value * 0.6).sp)
            } else if (cell.number > 0) {
                Text(
                    text = cell.number.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.6).sp, // 字体随格子大小自动缩放
                    color = getNumberColor(cell.number)
                )
            }
        } else if (cell.isFlagged) {
            Text("🚩", fontSize = (size.value * 0.6).sp)
        }
    }
}

fun getNumberColor(number: Int): Color {
    return when (number) {
        1 -> Color(0xFF1976D2)
        2 -> Color(0xFF388E3C)
        3 -> Color(0xFFD32F2F)
        4 -> Color(0xFF7B1FA2)
        5 -> Color(0xFFF57C00)
        else -> Color.Black
    }
}