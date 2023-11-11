package com.example.stratagemofvalors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.stratagemofvalors.ui.theme.StratagemOfValorsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val boardSize = 8


enum class GameResult {
    ONGOING, PLAYER_ONE_WINS, PLAYER_TWO_WINS, DRAW
}


enum class PieceType {
    FLAGSHIP, GUARD, RAIDER, CANNON, SENTINEL
}

enum class Player {
    ONE, TWO
}

enum class Action {
    NONE, MOVE, FIRE
}


data class GameState(
    val boardState: Array<Array<Piece?>>,
    val playerOneScore: Int,
    val playerTwoScore: Int,
    val gameResult: GameResult = GameResult.ONGOING
)

data class SelectedPieceState(
    val position: Pair<Int, Int>? = null,
    val action: Action = Action.NONE
)


data class Piece(val type: PieceType, val player: Player, var remainingShots: Int)

var explosionEventCounter = 0


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val startTime = System.currentTimeMillis()

        setContent {
            StratagemOfValorsTheme {
                var showOverlay by remember { mutableStateOf(true) }

                // Launching effect to delay the overlay display
                LaunchedEffect(key1 = "key") {
                    delay(2000) // Delay for one second
                    showOverlay = false // After delay, set to false to hide the overlay
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameBoard()

                    // Overlay Surface, which shows up for one second
                    if (showOverlay) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Black
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.splash_icon),
                                contentDescription = "Stratagem of Valors"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Scoreboard(playerOneScore: Int, playerTwoScore: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Player One: $playerOneScore", style = MaterialTheme.typography.titleMedium)
        Text("Player Two: $playerTwoScore", style = MaterialTheme.typography.titleMedium)
    }
}


// Function to apply AI's move to the board state
fun applyAiMove(
    aiMove: Move,
    boardStateState: MutableState<Array<Array<Piece?>>>,
    playerTwoScore: MutableState<Int>,
    explosionDisplay: MutableState<ExplosionEvent?>
) {
    val (fromPosition, toPosition) = aiMove
    val (fromRow, fromCol) = fromPosition
    val (toRow, toCol) = toPosition

    val boardState = boardStateState.value
    val pieceToMove = boardState[fromRow][fromCol]
    val targetPiece = boardState[toRow][toCol]

    // If there is a piece at the target position and it belongs to the opponent, capture it
    if (targetPiece != null && targetPiece.player != Player.TWO) {
        playerTwoScore.value += 1 // Increment score for capturing a piece
        boardState[toRow][toCol] = null // Remove the captured piece from the board
    }

    // If a piece is hit, set explosionDisplay to the hit position
    if (aiMove.action == Action.FIRE) { // You should replace pieceIsHit with the condition that checks for a hit
        // Replace hitRow and hitCol with actual hit position
        explosionDisplay?.value = ExplosionEvent(Pair(toRow, toCol), explosionEventCounter++)
    }

    // Move the piece
    boardState[toRow][toCol] = pieceToMove
    boardState[fromRow][fromCol] = null

    // Update the board state to trigger UI recomposition
    boardStateState.value = boardState.map { it.copyOf() }.toTypedArray()
}


fun decideBestAiAction(
    boardState: Array<Array<Piece?>>,
    playerTwoScore: MutableState<Int>
): Move? {
    // Generate all possible moves for AI
    val possibleActions = generateMoves(boardState, Player.TWO)

    // Initialize best move and score tracking
    var bestMove: Move? = null
    var bestValue = Int.MIN_VALUE // For maximizing player


    // Iterate over all possible moves to find the best one
    for (action in possibleActions) {
        // Create a hypothetical move by applying the current move
        val (newState, newP1Score, newP2Score) = applyMove(
            boardState, action, playerTwoScore.value, playerTwoScore.value
        )

        // Use minimax to evaluate the board after making the hypothetical move
        val moveValue = minimax(
            newState, // New board state after the move
            2, // Depth set to 2 for now as per your requirement
            false, // The next turn will be minimizing player (opponent)
            newP1Score, // New player one score
            newP2Score, // New player two score
            Player.ONE // After AI move, it will be player one's turn
        )

        // Check if the current move is better than the best found so far
        if (moveValue > bestValue) {
            bestValue = moveValue
            bestMove = action
        }
    }

    // Update playerTwoScore if a move is made
    if (bestMove != null) {
        val (_, newP1Score, newP2Score) = applyMove(
            boardState, bestMove, playerTwoScore.value, playerTwoScore.value
        )
        playerTwoScore.value = newP2Score // Update the score with the new value after move
    }

    return bestMove // Return the best move found or null if none are available
}


@Composable
fun GameBoard() {
    // At the top level of GameBoard()
    var currentPlayerTurn = remember { mutableStateOf(Player.ONE) }

    var playerOneScore = remember { mutableStateOf(0) }
    var playerTwoScore = remember { mutableStateOf(0) }

    // boardState now holds a state object that contains the array.
    // Changes to this state will trigger recompositions in the Composables that read it.
    val boardState: Array<Array<Piece?>> =
        remember { Array(boardSize) { arrayOfNulls<Piece?>(boardSize) } }
    val boardStateState = remember { mutableStateOf(boardState) }
    var selectedPieceState by remember { mutableStateOf(SelectedPieceState()) }

    // This state holds the position of the piece that has been selected for firing
    var firingStartPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var firingPiece by remember { mutableStateOf<Piece?>(null) }

    // This would be a map to keep track of piece visibility for the animation
    val pieceVisibility = remember { mutableStateMapOf<Piece, Boolean>().withDefault { true } }

    val isBoardInitialized = remember { mutableStateOf(false) }

    var gameResult by remember { mutableStateOf(GameResult.ONGOING) }

    var isBoardLocked by remember { mutableStateOf(false) }

    // Add state for the AI move indication
    val aiMoveFromPosition = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val aiMoveToPosition = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val aiIsFiring = remember { mutableStateOf<Boolean>(false) }

    val explosionDisplay = remember { mutableStateOf<ExplosionEvent?>(null) }


    if (!isBoardInitialized.value) {
        // Initialize player one's pieces
        boardState[0][3] = Piece(PieceType.FLAGSHIP, Player.ONE, 0) // D1
        boardState[0][2] = Piece(PieceType.GUARD, Player.ONE, 0) // C1
        boardState[0][4] = Piece(PieceType.GUARD, Player.ONE, 0) // E1
        boardState[1][2] = Piece(PieceType.CANNON, Player.ONE, 4) // C2
        boardState[1][4] = Piece(PieceType.CANNON, Player.ONE, 4) // E2
        // Raiders and Sentinels for Player One
        for (column in arrayOf(0, 1, 6, 7)) {
            boardState[0][column] = Piece(PieceType.RAIDER, Player.ONE, 0) // A1, B1, G1, H1
            boardState[1][column] = Piece(PieceType.SENTINEL, Player.ONE, 3) // A2, B2, G2, H2
        }

        // Initialize player two's pieces
        boardState[7][4] = Piece(PieceType.FLAGSHIP, Player.TWO, 0) // E8
        boardState[7][3] = Piece(PieceType.GUARD, Player.TWO, 0) // D8
        boardState[7][5] = Piece(PieceType.GUARD, Player.TWO, 0) // F8
        boardState[6][3] = Piece(PieceType.CANNON, Player.TWO, 4) // D7
        boardState[6][5] = Piece(PieceType.CANNON, Player.TWO, 4) // F7

        // Raiders and Sentinels for Player Two
        for (column in arrayOf(0, 1, 6, 7)) {
            boardState[7][column] = Piece(PieceType.RAIDER, Player.TWO, 0) // A8, B8, G8, H8
            boardState[6][column] = Piece(PieceType.SENTINEL, Player.TWO, 3) // A7, B7, G7, H7
        }

        isBoardInitialized.value = true
    }

    // Use the selected position to calculate valid moves
    val validMoves =
        selectedPieceState.position?.let {
            calculateValidMoves(it, boardStateState.value)
        }.orEmpty()


    // For simplicity, we'll only show the firing range when a Cannon or Sentinel is selected.
    val firingRange =
        selectedPieceState.position?.let {
            val testPiece = boardStateState.value[it.first][it.second]

            if (testPiece != null && testPiece.remainingShots > 0) {
                when (selectedPieceState.position?.let { boardStateState.value[it.first][it.second]?.type }) {
                    PieceType.CANNON -> selectedPieceState.position?.let {
                        calculateCannonFiringRange(
                            it,
                            boardStateState.value
                        )
                    }.orEmpty()

                    PieceType.SENTINEL -> selectedPieceState.position?.let {
                        calculateSentinelFiringRange(
                            it,
                            boardStateState.value
                        )
                    }.orEmpty()

                    else -> emptyList()
                }
            } else emptyList()
        }.orEmpty()

    LaunchedEffect(key1 = currentPlayerTurn.value, key2 = gameResult) {
        if (currentPlayerTurn.value == Player.TWO && gameResult == GameResult.ONGOING) {
            isBoardLocked =
                true // Lock the board to prevent Player One from making moves during AI's turn

            // Move AI thinking into a coroutine to avoid blocking the UI thread
            val aiActionDeferred = async(Dispatchers.Default) { // Run in background
                decideBestAiAction(boardStateState.value, playerTwoScore)
            }

            // Wait for the AI decision to be computed
            val aiAction = aiActionDeferred.await()

            // Set up the move indication before actually moving
            aiMoveFromPosition.value = aiAction?.from
            aiMoveToPosition.value = aiAction?.to
            aiIsFiring.value = aiAction?.action == Action.FIRE

            // Delay to show the indication for 1 second
            delay(500)

            // Once AI decision is ready, apply it on the main thread
            withContext(Dispatchers.Main) {
                when (aiAction?.action) {
                    Action.MOVE -> {
                        // Execute move
                        applyAiMove(aiAction, boardStateState, playerTwoScore, explosionDisplay)
                    }

                    Action.FIRE -> {
                        // Execute firing
                        executeFiring(
                            aiAction.from,
                            aiAction.to,
                            boardStateState,
                            playerOneScore,
                            playerTwoScore,
                            currentPlayerTurn
                        )
                    }

                    Action.NONE -> {
                        // No action is taken, possibly end the turn or wait
                    }

                    else -> {}
                }

                // Update game result and switch turns
                gameResult = checkEndGameCondition(
                    boardStateState.value,
                    playerOneScore.value,
                    playerTwoScore.value
                )

                // Switch turn back to Player One, unless the game has ended
                if (gameResult == GameResult.ONGOING) {
                    currentPlayerTurn.value = Player.ONE
                }

                // Clear the indication states after the move is shown
                aiMoveFromPosition.value = null
                aiMoveToPosition.value = null
                aiIsFiring.value = false

                isBoardLocked = false // Unlock the board for Player One's turn
            }
        }
    }



    Column(modifier = Modifier.padding(16.dp)) {
        Scoreboard(playerOneScore.value, playerTwoScore.value)

        Box(contentAlignment = Alignment.TopStart) {
            // Overlay Canvas to draw the firing line if aiIsFiring is true

            if (aiIsFiring.value) {
                val fromPosition = aiMoveFromPosition.value ?: return
                val toPosition = aiMoveToPosition.value ?: return

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                ) { // zIndex ensures it's drawn under the pieces
                    val squareSize = size.width / boardSize // Assuming square board for simplicity
                    val strokeWidth =
                        (squareSize / 15).coerceAtLeast(2.dp.toPx()) // Adjust stroke width as needed

                    // Assuming one square size is 40.dp as provided earlier
                    val squareSizePx = 40.dp.toPx()
                    val fromX = fromPosition.second * squareSizePx
                    val fromY = fromPosition.first * squareSizePx
                    val toX = toPosition.second * squareSizePx
                    val toY = toPosition.first * squareSizePx

                    // Determine the direction of the firing line
                    val directionX = toPosition.second.compareTo(fromPosition.second)
                    val directionY = toPosition.first.compareTo(fromPosition.first)

                    // Calculate start and end points based on the direction of the firing line
                    val startX = if (directionX != 0) {
                        fromX + (if (directionX > 0) squareSizePx else 0f)
                    } else {
                        fromX + squareSizePx / 2 // Center X for vertical lines
                    }

                    val startY = if (directionY != 0) {
                        fromY + (if (directionY > 0) squareSizePx else 0f)
                    } else {
                        fromY + squareSizePx / 2 // Center Y for horizontal lines
                    }

                    val endX = if (directionX != 0) {
                        toX + (if (directionX < 0) squareSizePx else 0f)
                    } else {
                        toX + squareSizePx / 2 // Center X for vertical lines
                    }

                    val endY = if (directionY != 0) {
                        toY + (if (directionY < 0) squareSizePx else 0f)
                    } else {
                        toY + squareSizePx / 2 // Center Y for horizontal lines
                    }

                    // Draw the firing line
                    drawLine(
                        color = Color.Red,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth
                    )
                }

            }
            Column {
                for (row in 0 until boardSize) {
                    Row {
                        for (column in 0 until boardSize) {

                            val piece = boardStateState.value[row][column]
                            val isSelected = selectedPieceState.position == row to column
                            val isPossibleMove = validMoves.contains(row to column)
                            val isInFiringRange = firingRange.contains(row to column)

                            BoardSquare(
                                row, column, piece, isSelected, isPossibleMove, isInFiringRange,
                                firePiece = { firingPiece!!.type },
                                onFireClick = {

                                    if (isBoardLocked) return@BoardSquare
                                    if (gameResult != GameResult.ONGOING) return@BoardSquare

                                    if (firingPiece!!.type == PieceType.SENTINEL ||
                                        firingPiece!!.type == PieceType.CANNON
                                    ) {
                                        if (firingPiece!!.remainingShots <= 0)
                                            return@BoardSquare
                                    }

                                    val currentPosition = row to column
                                    selectedPieceState =
                                        SelectedPieceState(currentPosition, Action.FIRE)

                                    // When the state is set to FIRE, perform the firing logic

                                    if (selectedPieceState.action == Action.FIRE) {

                                        explosionDisplay.value = ExplosionEvent(
                                            selectedPieceState!!.position!!,
                                            explosionEventCounter++
                                        )

                                        executeFiring(
                                            firingStartPosition!!,
                                            selectedPieceState!!.position!!,
                                            boardStateState,
                                            playerOneScore,
                                            playerTwoScore,
                                            currentPlayerTurn
                                        )

                                        if (firingPiece!!.type == PieceType.SENTINEL ||
                                            firingPiece!!.type == PieceType.CANNON
                                        ) {
                                            firingPiece!!.remainingShots--
                                        }

                                        gameResult = checkEndGameCondition(
                                            boardStateState.value,
                                            playerOneScore.value,
                                            playerTwoScore.value
                                        )
                                        // Check for end game conditions after firing is made

                                        selectedPieceState =
                                            SelectedPieceState() // Reset the state after firing
                                    }
                                },
                                aiMoveFromPosition.value,
                                aiMoveToPosition.value,
                                aiIsFiring.value,
                                explosionDisplay.value,
                                onExplosionEnd = {
                                    explosionDisplay.value =
                                        null // Reset explosion state after it's shown
                                }
                            ) { possible ->

                                if (isBoardLocked) return@BoardSquare
                                if (gameResult != GameResult.ONGOING) return@BoardSquare

                                val currentPosition = row to column
                                val currentPiece = boardStateState.value[row][column]


                                when {
                                    // Check if it's the correct player's turn and if the piece can be selected
                                    currentPiece != null && currentPiece.player == currentPlayerTurn.value &&
                                            (selectedPieceState.position == null || selectedPieceState.position != currentPosition) &&
                                            !possible -> {
                                        selectedPieceState =
                                            SelectedPieceState(currentPosition, Action.MOVE)
                                        firingStartPosition = selectedPieceState.position
                                        firingPiece = currentPiece
                                    }

                                    // Deselect the piece if the same piece is selected
                                    currentPiece != null && selectedPieceState.position == currentPosition -> {
                                        selectedPieceState = SelectedPieceState()
                                    }

                                    // Execute the move if a move is possible to this square and it's the correct player's turn
                                    selectedPieceState.action == Action.MOVE && possible &&
                                            boardStateState.value[selectedPieceState.position!!.first][selectedPieceState.position!!.second]?.player == currentPlayerTurn.value -> {

                                        val fromPosition = selectedPieceState.position!!
                                        val toPosition = currentPosition
                                        val pieceToMove =
                                            boardStateState.value[fromPosition.first][fromPosition.second]

                                        if (pieceToMove != null) {
                                            val targetPiece =
                                                boardStateState.value[toPosition.first][toPosition.second]
                                            if (targetPiece != null && targetPiece.player != pieceToMove?.player) {
                                                // Increase score based on the player of the moving piece
                                                if (pieceToMove?.player == Player.ONE) {
                                                    playerOneScore.value++
                                                } else {
                                                    playerTwoScore.value++
                                                }
                                            }

                                            // Remove the taken piece from the board
                                            boardStateState.value[toPosition.first][toPosition.second] =
                                                null

                                            // Update the board state array
                                            boardStateState.value[fromPosition.first][fromPosition.second] =
                                                null
                                            boardStateState.value[toPosition.first][toPosition.second] =
                                                pieceToMove

                                            // Clone the board state array to trigger recomposition
                                            val newBoardState =
                                                boardStateState.value.map { it.clone() }
                                                    .toTypedArray()
                                            boardStateState.value = newBoardState
                                        }

                                        gameResult = checkEndGameCondition(
                                            boardStateState.value,
                                            playerOneScore.value,
                                            playerTwoScore.value
                                        )
                                        // Check for end game conditions after move is made

                                        // Reset selected piece state
                                        selectedPieceState = SelectedPieceState()

                                        // Change the turn to the other player
                                        currentPlayerTurn.value =
                                            if (currentPlayerTurn.value == Player.ONE) Player.TWO else Player.ONE

                                    }
                                    // ... additional logic for handling other actions ...
                                }

                            }
                        }
                    }
                }
            }
        }

        if (isBoardLocked) {
            Text(
                "Thinking...",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.zIndex(2f)
            )
        }

        when (gameResult) {
            GameResult.PLAYER_ONE_WINS -> {
                Text("PLAYER ONE WINS!", modifier = Modifier.zIndex(2f))
            }

            GameResult.PLAYER_TWO_WINS -> {
                // Handle Player Two wins
                Text("PLAYER TWO WINS!", modifier = Modifier.zIndex(2f))
            }

            GameResult.DRAW -> {
                // Handle draw
                Text("IT'S A DRAW!", modifier = Modifier.zIndex(2f))
            }

            GameResult.ONGOING -> {
                // Game is ongoing
                Text("")
            }
        }
    }
}


@Composable
fun BoardSquare(
    row: Int,
    column: Int,
    piece: Piece?,
    isSelected: Boolean,
    isPossibleMove: Boolean,
    isInFiringRange: Boolean,
    firePiece: () -> PieceType?,
    onFireClick: () -> Unit,
    aiMoveFrom: Pair<Int, Int>?,
    aiMoveTo: Pair<Int, Int>?,
    aiIsFiring: Boolean,
    explosionEvent: ExplosionEvent?,
    onExplosionEnd: () -> Unit,
    onMoveClick: (isPossibleMove: Boolean) -> Unit
) {
    val squareColor = if ((row + column) % 2 == 0) Color.LightGray else Color.DarkGray
    // Determine the background color based on AI and player moves
    val backgroundColor = when {
        aiMoveFrom == row to column || aiMoveTo == row to column -> Color.Magenta // AI's move indication in purple
        isPossibleMove -> Color(0xFF90EE90) // Valid moves for player in green
        else -> squareColor // Default square color
    }

    // Determine the border color based on selection and firing range
    val borderColor = when {
        isSelected -> Color.Green // Selected piece
        isInFiringRange && !aiIsFiring -> Color.Red // Firing range when not firing
        aiIsFiring && aiMoveFrom == row to column -> Color.Red // Firing range for AI
        else -> squareColor // Default to square color if none of the above
    }

    val borderModifier = Modifier.border(width = 2.dp, color = borderColor)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(backgroundColor)
            .then(borderModifier) // Apply the border modifier after the background
            .pointerInput(isPossibleMove) {
                detectTapGestures(
                    onDoubleTap = {
                        val firingPieceType = firePiece()
                        if (firingPieceType == PieceType.CANNON || firingPieceType == PieceType.SENTINEL) onFireClick()
                        else onMoveClick(isPossibleMove)
                    },
                    onTap = { onMoveClick(isPossibleMove) }
                )
            }
    ) {
        explosionEvent?.let { event ->
            if (explosionEvent.position == (row to column)) {
                // Call AnimatedExplosion with the appropriate size and modifier
                AnimatedExplosion(modifier = Modifier.size(40.dp))

                LaunchedEffect(event.id) { // Use the unique event ID as the key
                    delay(500) // Explosion display duration
                    onExplosionEnd() // Reset the explosion display state
                }
            }
        }

        piece?.let {
            PieceIcon(it) // Display piece icon if there's no explosion event for this square
        }
    }
}

data class ExplosionEvent(val position: Pair<Int, Int>, val id: Int)
