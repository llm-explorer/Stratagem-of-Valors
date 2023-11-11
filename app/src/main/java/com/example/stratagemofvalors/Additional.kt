package com.example.stratagemofvalors

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.repeatCount
import coil.size.Scale
import kotlin.math.sign

fun getMovesInLine(
    row: Int,
    column: Int,
    boardState: Array<Array<Piece?>>,
    includeDiagonals: Boolean,
    includeStraight: Boolean,
    player: Player
): List<Pair<Int, Int>> {
    val directions = mutableListOf<Pair<Int, Int>>()
    if (includeStraight) {
        directions.addAll(listOf(Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0)))
    }
    if (includeDiagonals) {
        directions.addAll(listOf(Pair(1, 1), Pair(1, -1), Pair(-1, -1), Pair(-1, 1)))
    }

    val moves = mutableListOf<Pair<Int, Int>>()
    for (direction in directions) {
        var currentRow = row + direction.first
        var currentColumn = column + direction.second
        while (currentRow in 0..7 && currentColumn in 0..7) {
            // Stop at the first piece encountered
            if (boardState[currentRow][currentColumn] != null) {
                // If it's an enemy piece, include it in valid moves, then break
                if (boardState[currentRow][currentColumn]?.player != player) {
                    moves.add(Pair(currentRow, currentColumn))
                }
                break
            }
            moves.add(Pair(currentRow, currentColumn))
            currentRow += direction.first
            currentColumn += direction.second
        }
    }
    return moves
}

fun getMovesInAllDirections(
    row: Int,
    column: Int,
    distance: Int,
    boardState: Array<Array<Piece?>>,
    player: Player
): List<Pair<Int, Int>> {
    val moves = mutableListOf<Pair<Int, Int>>()
    val directions = listOf(
        Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0),
        Pair(1, 1), Pair(1, -1), Pair(-1, -1), Pair(-1, 1)
    )
    for (direction in directions) {
        val newRow = row + direction.first * distance
        val newColumn = column + direction.second * distance
        if (newRow in 0..7 && newColumn in 0..7) {
            val encounteredPiece = boardState[newRow][newColumn]
            if (encounteredPiece == null || encounteredPiece.player != player) {
                moves.add(Pair(newRow, newColumn))
            }
        }
    }
    return moves
}

// This function now uses the actual game rules to calculate valid moves.
fun calculateValidMoves(
    selectedPosition: Pair<Int, Int>,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {
    val (row, column) = selectedPosition
    val piece = boardState[row][column] ?: return emptyList()
    val player = piece.player
    val moves = mutableListOf<Pair<Int, Int>>()

    when (piece.type) {
        PieceType.FLAGSHIP, PieceType.SENTINEL -> {
            // The Flagship and Sentinel can move one square in any direction.
            moves.addAll(getMovesInAllDirections(row, column, 1, boardState, player))
        }

        PieceType.GUARD, PieceType.RAIDER -> {
            // The Guard can move any number of squares along rows or columns,
            // and the Raider can move any number of squares diagonally.
            val includeDiagonals = piece.type == PieceType.RAIDER
            moves.addAll(
                getMovesInLine(
                    row,
                    column,
                    boardState,
                    includeDiagonals,
                    !includeDiagonals,
                    player
                )
            )
        }
        // Cannons do not move but have attack capabilities, which are not covered by this logic.
        PieceType.CANNON -> {
            // No movement, handle attack logic separately.
        }
    }

    return moves
}


fun executeFiring(
    fromPosition: Pair<Int, Int>,
    toPosition: Pair<Int, Int>,
    boardStateState: MutableState<Array<Array<Piece?>>>,
    playerOneScore: MutableState<Int>,
    playerTwoScore: MutableState<Int>,
    currentPlayerTurn: MutableState<Player>
) {
    val (rowFrom, colFrom) = fromPosition
    val (rowTo, colTo) = toPosition
    val firingPiece = boardStateState.value[rowFrom][colFrom]
    val boardState = boardStateState.value

    // Determine the direction of firing
    val direction = Pair(rowTo - rowFrom, colTo - colFrom)
    val normalizedDirection =
        Pair(sign(direction.first.toDouble()).toInt(), sign(direction.second.toDouble()).toInt())

    // Calculate the firing range
    val firingRange: List<Pair<Int, Int>> =
        calculateFiringRange(fromPosition, normalizedDirection, firingPiece, boardState)

    for (targetPosition in firingRange) {
        val (targetRow, targetCol) = targetPosition
        val targetPiece = boardState[targetRow][targetCol]

        // Check if there's a piece in the line of fire
        if (targetPiece != null) {
            // Determine the outcome based on the hit piece
            processFiringOutcome(targetPiece, firingPiece, playerOneScore, playerTwoScore)

            // Remove the target piece from the board
            boardStateState.value[targetRow][targetCol] = null
            break // Stop firing after the first hit
        }
    }

    // After firing logic, change the player's turn
    currentPlayerTurn.value = if (currentPlayerTurn.value == Player.ONE) Player.TWO else Player.ONE
}

// Helper function to process the firing outcome
fun processFiringOutcome(
    targetPiece: Piece,
    firingPiece: Piece?,
    playerOneScore: MutableState<Int>,
    playerTwoScore: MutableState<Int>
) {
    if (targetPiece.player != firingPiece?.player) {
        // Hit an enemy piece
        if (firingPiece?.player == Player.ONE) {
            playerOneScore.value++ // Player One scores
        } else {
            playerTwoScore.value++ // Player Two scores
        }
    } else {
        // Friendly fire
        if (firingPiece?.player == Player.ONE) {
            playerOneScore.value-- // Penalize Player One
        } else {
            playerTwoScore.value-- // Penalize Player Two
        }
    }
}

// Calculate the firing range for the piece based on the direction
fun calculateFiringRange(
    fromPosition: Pair<Int, Int>,
    direction: Pair<Int, Int>,
    firingPiece: Piece?,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {

    if (firingPiece != null && firingPiece.remainingShots > 0) {
        return when (firingPiece.type) {
            PieceType.CANNON -> calculateCannonFiringRange(fromPosition, direction, boardState)
            PieceType.SENTINEL -> calculateSentinelFiringRange(fromPosition, direction, boardState)
            else -> emptyList() // Only Cannons and Sentinels can fire
        }
    } else {
        return emptyList()
    }
}

fun calculateCannonFiringRange(
    fromPosition: Pair<Int, Int>,
    direction: Pair<Int, Int>,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {
    // Cannons have a range of 4
    return calculateFiringLine(fromPosition, direction, 4, boardState)
}

fun calculateSentinelFiringRange(
    fromPosition: Pair<Int, Int>,
    direction: Pair<Int, Int>,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {
    // Sentinels have a range of 3
    return calculateFiringLine(fromPosition, direction, 3, boardState)
}

fun calculateFiringLine(
    fromPosition: Pair<Int, Int>,
    direction: Pair<Int, Int>,
    range: Int,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {
    val (rowDirection, colDirection) = direction
    var (currentRow, currentColumn) = fromPosition
    val firingLine = mutableListOf<Pair<Int, Int>>()

    for (i in 1..range) {
        currentRow += rowDirection
        currentColumn += colDirection

        if (currentRow !in 0..7 || currentColumn !in 0..7) {
            break // Stop if out of board bounds
        }

        firingLine.add(Pair(currentRow, currentColumn))

        if (boardState[currentRow][currentColumn] != null) {
            break // Stop if a piece is encountered
        }
    }

    return firingLine
}


fun Array<Piece?>.clone() = this.copyOf()


@Composable
fun PieceIcon(piece: Piece) {
    val imageRes = when (piece.type) {
        PieceType.FLAGSHIP -> if (piece.player == Player.ONE) R.drawable.b_flagship else R.drawable.r_flagship
        PieceType.GUARD -> if (piece.player == Player.ONE) R.drawable.b_guard else R.drawable.r_guard
        PieceType.RAIDER -> if (piece.player == Player.ONE) R.drawable.b_raider else R.drawable.r_raider
        PieceType.CANNON -> if (piece.player == Player.ONE) R.drawable.b_cannon else R.drawable.r_cannon
        PieceType.SENTINEL -> if (piece.player == Player.ONE) R.drawable.b_sentinel else R.drawable.r_sentinel
    }
    Image(painter = painterResource(id = imageRes), contentDescription = piece.type.name)
}


@Composable
fun AnimatedExplosion(modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
    Image(
        painter = rememberAsyncImagePainter(
            ImageRequest.Builder(context).repeatCount(-1).data(data = R.drawable.explosion_animated)
                .apply(block = {
                    scale(Scale.FILL)

                }).build(), imageLoader = imageLoader
        ),
        contentDescription = null,
        modifier = modifier.fillMaxWidth(),
    )
}

fun calculateCannonFiringRange(
    selectedPosition: Pair<Int, Int>,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {
    val (row, column) = selectedPosition
    val moves = mutableListOf<Pair<Int, Int>>()

    // Cannons can fire straight and diagonally in all directions.
    val directions = listOf(
        Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0), // Straight directions
        Pair(1, 1), Pair(1, -1), Pair(-1, -1), Pair(-1, 1) // Diagonal directions
    )

    for (direction in directions) {
        moves.addAll(calculateFiringLine(selectedPosition, direction, 4, boardState))
    }

    return moves
}

fun calculateSentinelFiringRange(
    selectedPosition: Pair<Int, Int>,
    boardState: Array<Array<Piece?>>
): List<Pair<Int, Int>> {
    val (row, column) = selectedPosition
    val moves = mutableListOf<Pair<Int, Int>>()

    // Sentinels can also fire straight and diagonally in all directions.
    val directions = listOf(
        Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0), // Straight directions
        Pair(1, 1), Pair(1, -1), Pair(-1, -1), Pair(-1, 1) // Diagonal directions
    )

    for (direction in directions) {
        moves.addAll(calculateFiringLine(selectedPosition, direction, 3, boardState))
    }

    return moves
}
