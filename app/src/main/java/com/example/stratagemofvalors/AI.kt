package com.example.stratagemofvalors

fun scoreForRemainingPieces(boardState: Array<Array<Piece?>>, player: Player): Int {
    // Define values for each piece type if they differ, for simplicity let's assume a flat score
    val pieceValue = mapOf(
        PieceType.FLAGSHIP to 100,
        PieceType.GUARD to 20,
        PieceType.RAIDER to 15,
        PieceType.CANNON to 25,
        PieceType.SENTINEL to 30
    )

    // Sum up the score based on the remaining pieces
    return boardState.flatten().filter { it?.player == player }.sumOf { pieceValue[it!!.type] ?: 0 }
}

fun isFlagshipVulnerable(
    boardState: Array<Array<Piece?>>,
    player: Player
): Boolean {
    // Find the flagship's position for the given player
    val flagshipPosition = findFlagshipPosition(boardState, player) ?: return false

    // Check vulnerability from moving pieces (Guards and Raiders)
    if (isFlagshipVulnerableFromMovingPieces(boardState, flagshipPosition, player)) {
        return true
    }

    // Check vulnerability from firing pieces (Cannons and Sentinels)
    val enemy = if (player == Player.ONE) Player.TWO else Player.ONE
    return boardState.indices.any { row ->
        boardState[row].indices.any { col ->
            val piece = boardState[row][col]
            // Proceed if the piece belongs to the enemy and is a Cannon or Sentinel
            if (piece != null && piece.player == enemy && (piece.type == PieceType.CANNON || piece.type == PieceType.SENTINEL)) {
                // Check if the Cannon or Sentinel can fire at the flagship
                canCannonOrSentinelFireAtFlagship(
                    boardState,
                    Pair(row, col),
                    flagshipPosition,
                    getFiringRangeForPieceType(piece.type)
                )
            } else {
                false
            }
        }
    }
}

// Helper function to determine the firing range based on piece type
fun getFiringRangeForPieceType(pieceType: PieceType): Int {
    return when (pieceType) {
        PieceType.CANNON -> 4 // Cannons have a range of 4
        PieceType.SENTINEL -> 3 // Sentinels have a range of 3
        else -> 0 // Other types do not have a firing range
    }
}

// This function performs the check for direct threats from pieces that can move to the flagship's position
fun isFlagshipVulnerableFromMovingPieces(
    boardState: Array<Array<Piece?>>,
    flagshipPosition: Pair<Int, Int>,
    player: Player
): Boolean {
    val enemy = if (player == Player.ONE) Player.TWO else Player.ONE
    val adjacentPositions = listOf(
        Pair(1, 0), Pair(-1, 0), // Vertical
        Pair(0, 1), Pair(0, -1), // Horizontal
        Pair(1, 1), Pair(-1, -1), // Diagonal
        Pair(-1, 1), Pair(1, -1)  // Diagonal
    )

    // Check the vicinity of the flagship for immediate threats from moving pieces
    for (offset in adjacentPositions) {
        val checkRow = flagshipPosition.first + offset.first
        val checkCol = flagshipPosition.second + offset.second
        if (checkRow !in 0..7 || checkCol !in 0..7) continue

        boardState[checkRow][checkCol]?.let {
            if (it.player == enemy && canPieceCaptureFlagship(
                    boardState,
                    Pair(checkRow, checkCol),
                    player
                )
            ) {
                return true
            }
        }
    }
    return false
}


// Helper function to find the flagship's position
fun findFlagshipPosition(boardState: Array<Array<Piece?>>, player: Player): Pair<Int, Int>? {
    for (row in boardState.indices) {
        for (col in boardState[row].indices) {
            val piece = boardState[row][col]
            if (piece?.type == PieceType.FLAGSHIP && piece.player == player) {
                return Pair(row, col)
            }
        }
    }
    return null
}


fun canPieceCaptureFlagship(
    boardState: Array<Array<Piece?>>,
    fromPosition: Pair<Int, Int>,
    player: Player
): Boolean {
    val (fromRow, fromCol) = fromPosition
    val enemyPlayer = if (player == Player.ONE) Player.TWO else Player.ONE

    // Determine the piece type at the 'from' position
    val piece = boardState[fromRow][fromCol] ?: return false // Early exit if no piece

    val directions = getMovementDirectionsForPieceType(piece.type)

    for (direction in directions) {
        var currentRow = fromRow
        var currentCol = fromCol

        while (true) {
            currentRow += direction.first
            currentCol += direction.second

            // Break if out of bounds
            if (currentRow !in boardState.indices || currentCol !in boardState[0].indices) break

            val currentPiece = boardState[currentRow][currentCol]

            // Skip if current square is empty and continue in the same direction
            if (currentPiece == null) continue

            // Break if it's a friendly piece, as it can't capture its own team's flagship
            if (currentPiece.player == player) break

            // Check if an enemy piece is a flagship and return true if so
            if (currentPiece.player == enemyPlayer && currentPiece.type == PieceType.FLAGSHIP) return true

            // Break if it's any other piece to stop further checking in this line
            break
        }
    }

    return false // If no conditions above are met, return false
}

fun getMovementDirectionsForPieceType(pieceType: PieceType): List<Pair<Int, Int>> {
    return when (pieceType) {
        PieceType.GUARD -> listOf(
            Pair(0, 1),
            Pair(0, -1),
            Pair(1, 0),
            Pair(-1, 0)
        ) // Cardinals only
        PieceType.RAIDER -> listOf(
            Pair(1, 1),
            Pair(1, -1),
            Pair(-1, 1),
            Pair(-1, -1)
        ) // Diagonals only
        PieceType.CANNON, PieceType.SENTINEL -> listOf(
            Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0),
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
        ) // All directions for firing, but need to consider range limitations elsewhere
        PieceType.FLAGSHIP -> listOf(
            Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0),
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
        ) // Flagship can potentially move in all directions, typically one square at a time
        else -> emptyList() // Other types or unknown types have no defined movement
    }
}


fun canCannonOrSentinelFireAtFlagship(
    boardState: Array<Array<Piece?>>,
    firingPosition: Pair<Int, Int>,
    targetPosition: Pair<Int, Int>,
    firingRange: Int
): Boolean {
    // Directions in which the cannon or sentinel can fire
    val directions = listOf(
        Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0), // Straight directions
        Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1) // Diagonal directions
    )

    for (direction in directions) {
        var currentRow = firingPosition.first
        var currentCol = firingPosition.second
        var steps = 0

        while (steps < firingRange) {
            currentRow += direction.first
            currentCol += direction.second
            steps++

            // Break if out of bounds
            if (currentRow !in boardState.indices || currentCol !in boardState[0].indices) break

            val currentPiece = boardState[currentRow][currentCol]
            // If we hit any piece before reaching the end of range, break. Line of sight must be clear.
            if (currentPiece != null) {
                // If the current piece is the target and it's the flagship, return true.
                if (Pair(
                        currentRow,
                        currentCol
                    ) == targetPosition && currentPiece.type == PieceType.FLAGSHIP
                ) {
                    return true
                }
                break
            }
        }
    }

    return false // No line of sight to the flagship within range in any direction
}


fun calculatePieceMobility(boardState: Array<Array<Piece?>>, player: Player): Int {
    var mobilityScore = 0

    for (row in boardState.indices) {
        for (col in boardState[row].indices) {
            val piece = boardState[row][col]
            if (piece?.player == player) {
                mobilityScore += when (piece.type) {
                    PieceType.FLAGSHIP -> calculateFlagshipMobility(row, col, boardState)
                    PieceType.GUARD -> calculateLineMoveMobility(row, col, boardState, true, false)
                    PieceType.RAIDER -> calculateLineMoveMobility(row, col, boardState, false, true)
                    PieceType.CANNON -> calculateShootingMobility(row, col, boardState, 4)
                    PieceType.SENTINEL -> calculateShootingMobility(
                        row,
                        col,
                        boardState,
                        3
                    ) + calculateSentinelMoveMobility(row, col, boardState)
                }
            }
        }
    }

    return mobilityScore
}

fun calculateFlagshipMobility(row: Int, col: Int, boardState: Array<Array<Piece?>>): Int {
    // The Flagship can move one square in any direction.
    val directions = listOf(
        Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
        Pair(0, -1), /* {current position} */ Pair(0, 1),
        Pair(1, -1), Pair(1, 0), Pair(1, 1)
    )

    return directions.count { (dRow, dCol) ->
        val newRow = row + dRow
        val newCol = col + dCol
        // Check if the new position is within bounds and free
        newRow in boardState.indices && newCol in boardState[0].indices && boardState[newRow][newCol] == null
    }
}

fun calculateLineMoveMobility(
    row: Int,
    col: Int,
    boardState: Array<Array<Piece?>>,
    straight: Boolean,
    diagonal: Boolean
): Int {
    var moveCount = 0
    val directions = mutableListOf<Pair<Int, Int>>()

    if (straight) {
        directions.addAll(listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)))
    }
    if (diagonal) {
        directions.addAll(listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)))
    }

    directions.forEach { (dRow, dCol) ->
        var currentRow = row + dRow
        var currentCol = col + dCol
        while (currentRow in 0..7 && currentCol in 0..7 && boardState[currentRow][currentCol] == null) {
            moveCount++
            currentRow += dRow
            currentCol += dCol
        }
    }

    return moveCount
}

fun calculateShootingMobility(
    row: Int,
    col: Int,
    boardState: Array<Array<Piece?>>,
    range: Int
): Int {
    // Cannon and Sentinel firing mobility is calculated by potential shots within their range
    val directions = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
    var fireCount = 0

    directions.forEach { (dRow, dCol) ->
        var currentRow = row
        var currentCol = col
        var distance = 0
        while (++distance <= range) {
            currentRow += dRow
            currentCol += dCol
            // If the square is within bounds
            if (currentRow !in 0..7 || currentCol !in 0..7) break
            // If the line of sight is blocked by any piece, stop considering more squares in this direction
            if (boardState[currentRow][currentCol] != null) break
            fireCount++ // Increment for each unblocked square within range
        }
    }

    return fireCount
}

fun calculateSentinelMoveMobility(row: Int, col: Int, boardState: Array<Array<Piece?>>): Int {
    // Sentinel moving mobility: can move one square in any direction
    val directions = listOf(
        Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1), // Straight directions
        Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1) // Diagonal directions
    )

    return directions.count { (dRow, dCol) ->
        val newRow = row + dRow
        val newCol = col + dCol
        // Check if the new position is within bounds and free
        newRow in 0..7 && newCol in 0..7 && boardState[newRow][newCol] == null
    }
}


fun controlOfCenter(boardState: Array<Array<Piece?>>, player: Player): Int {
    val centerPositions = listOf(Pair(3, 3), Pair(3, 4), Pair(4, 3), Pair(4, 4))
    var controlScore = 0

    centerPositions.forEach { (row, col) ->
        boardState[row][col]?.let {
            if (it.player == player) {
                // You may assign different values based on piece type importance
                controlScore += when (it.type) {
                    PieceType.FLAGSHIP -> 4 // Flagship in the center might be weighted more heavily
                    PieceType.GUARD -> 3 // Guards might be given a value reflecting their strategic position
                    PieceType.RAIDER -> 3 // Raiders likewise
                    PieceType.CANNON -> 2 // Cannons might be less because they are less mobile
                    PieceType.SENTINEL -> 2 // Sentinels might also be valued for their range control
                    else -> 0 // No other types are expected as per current enumeration
                }
            }
        }
    }

    return controlScore
}


fun remainingAmmunitionScore(boardState: Array<Array<Piece?>>, player: Player): Int {
    var ammunitionScore = 0
    val cannonAmmunitionValue = 10 // Value per remaining cannon shot
    val sentinelAmmunitionValue = 15 // Value per remaining sentinel shot

    // Traverse the board to find Cannons and Sentinels and add up their remaining ammunition scores.
    for (row in boardState.indices) {
        for (col in boardState[row].indices) {
            val piece = boardState[row][col]

            if (piece?.player == player) {
                when (piece.type) {
                    PieceType.CANNON -> {
                        // For each Cannon, add to the score based on its remaining shots
                        ammunitionScore += piece.remainingShots * cannonAmmunitionValue
                    }

                    PieceType.SENTINEL -> {
                        // For each Sentinel, add to the score based on its remaining shots
                        ammunitionScore += piece.remainingShots * sentinelAmmunitionValue
                    }

                    else -> {
                        // Other pieces do not have ammunition, so they do not contribute to this score
                    }
                }
            }
        }
    }

    return ammunitionScore
}


fun evaluateBoardForAI(boardState: Array<Array<Piece?>>, playerTwoScore: Int): Int {
    // Use the helper functions to calculate the AI's score
    var score = playerTwoScore * 10 // Assuming each score point is worth 10

    // Add score for remaining pieces
    score += scoreForRemainingPieces(boardState, Player.TWO)

    // Subtract score if AI's flagship is vulnerable
    if (isFlagshipVulnerable(boardState, Player.TWO)) {
        score -= 200 // Assuming losing the flagship is a significant disadvantage
    }

    val fsp = findFlagshipPosition(boardState, Player.TWO)

    if (fsp == null) {
        // Flagship lost!
        score = Int.MIN_VALUE
    }

    // Add score for piece mobility
    score += calculatePieceMobility(boardState, Player.TWO)

    // Add score for control of the center
    score += controlOfCenter(boardState, Player.TWO)

    // Add score for remaining ammunition
    score += remainingAmmunitionScore(boardState, Player.TWO)

    return score
}


//****************************** MINMAX


fun minimax(
    boardState: Array<Array<Piece?>>,
    depth: Int,
    isMaximizingPlayer: Boolean,
    playerOneScore: Int,
    playerTwoScore: Int,
    currentPlayer: Player
): Int {

    // Base case: if the maximum depth is reached or the game is over
    if (depth == 0 || checkEndGameCondition(
            boardState,
            playerOneScore,
            playerTwoScore
        ) != GameResult.ONGOING
    ) {
        return evaluateBoardForAI(
            boardState,
            if (currentPlayer == Player.ONE) playerOneScore else playerTwoScore
        )
    }

    if (isMaximizingPlayer) {
        var bestValue = Int.MIN_VALUE

        // Generate all possible moves for AI
        val possibleMoves = generateMoves(boardState, currentPlayer)
        for (move in possibleMoves) {
            // Apply each move and get the new state

            val (newState, newP1Score, newP2Score) = applyMove(
                boardState,
                move,
                playerOneScore,
                playerTwoScore
            )
            val value = minimax(
                newState,
                depth - 1,
                false,
                newP1Score,
                newP2Score,
                if (currentPlayer == Player.ONE) Player.TWO else Player.ONE
            )

            bestValue = maxOf(bestValue, value)
        }
        return bestValue
    } else {
        var bestValue = Int.MAX_VALUE

        // Generate all possible moves for the opponent
        val possibleMoves = generateMoves(boardState, currentPlayer)
        for (move in possibleMoves) {
            // Apply each move and get the new state

            val (newState, newP1Score, newP2Score) = applyMove(
                boardState,
                move,
                playerOneScore,
                playerTwoScore
            )
            val value = minimax(
                newState,
                depth - 1,
                true,
                newP1Score,
                newP2Score,
                if (currentPlayer == Player.ONE) Player.TWO else Player.ONE
            )

            bestValue = minOf(bestValue, value)
        }
        return bestValue
    }
}


fun applyMove(
    boardState: Array<Array<Piece?>>,
    move: Move,
    playerOneScore: Int,
    playerTwoScore: Int
): Triple<Array<Array<Piece?>>, Int, Int> {

    // Create a deep copy of the boardState to avoid mutating the original board
    val newBoardState =
        boardState.map { row -> row.map { piece -> piece?.copy() }.toTypedArray() }.toTypedArray()
    var newPlayerOneScore = playerOneScore
    var newPlayerTwoScore = playerTwoScore

    // Destructure the move for ease of use
    val (from, to, action) = move

    when (action) {
        Action.MOVE -> {
            // Get the piece at the 'from' location and the potential target location
            val pieceToMove = newBoardState[from.first][from.second]
            val targetPiece = newBoardState[to.first][to.second]

            // Move the piece if the destination is empty or has an opponent's piece
            if (pieceToMove != null && (targetPiece == null || targetPiece.player != pieceToMove.player)) {
                // If capturing a piece, increment the appropriate score
                if (targetPiece != null) {
                    if (targetPiece.player == Player.ONE) newPlayerTwoScore++
                    else newPlayerOneScore++
                }

                // Move the piece in the new board state and clear the 'from' location
                newBoardState[to.first][to.second] = pieceToMove
                newBoardState[from.first][from.second] = null
            }
        }

        Action.FIRE -> {
            // Get the piece that is firing
            val firingPiece = newBoardState[from.first][from.second]

            // Perform firing action only if there's a piece and it has shots remaining
            if (firingPiece != null && firingPiece.remainingShots > 0) {
                // Decrement shots for firing action
                firingPiece.remainingShots--

                // Remove the target piece if it belongs to the opponent
                val targetPiece = newBoardState[to.first][to.second]
                if (targetPiece != null && targetPiece.player != firingPiece.player) {
                    newBoardState[to.first][to.second] = null // Remove the target piece

                    // Update score for capturing an opponent's piece
                    if (firingPiece.player == Player.TWO) newPlayerOneScore++
                    else newPlayerTwoScore++
                }
            }
        }

        Action.NONE -> {
            // No action needed, but included for completeness
        }
    }

    return Triple(newBoardState, newPlayerOneScore, newPlayerTwoScore)
}


fun checkEndGameCondition(
    boardState: Array<Array<Piece?>>,
    playerOneScore: Int,
    playerTwoScore: Int
): GameResult {
    val playerOneFlagship =
        boardState.any { row -> row.any { piece -> piece?.type == PieceType.FLAGSHIP && piece.player == Player.ONE } }
    val playerTwoFlagship =
        boardState.any { row -> row.any { piece -> piece?.type == PieceType.FLAGSHIP && piece.player == Player.TWO } }
    val anyPieceOne =
        boardState.any { row -> row.any { piece -> piece?.type != null && piece.player == Player.ONE } }
    val anyPieceTwo =
        boardState.any { row -> row.any { piece -> piece?.type != null && piece.player == Player.TWO } }

    return when {
        // If either flagship is gone, that player loses
        !playerOneFlagship -> GameResult.PLAYER_TWO_WINS
        !playerTwoFlagship -> GameResult.PLAYER_ONE_WINS
        // Both flagships are gone or it's a stalemate situation
        playerOneFlagship && playerTwoFlagship && !anyPieceOne && !anyPieceTwo -> {
            when {
                playerOneScore > playerTwoScore -> GameResult.PLAYER_ONE_WINS
                playerTwoScore > playerOneScore -> GameResult.PLAYER_TWO_WINS
                else -> GameResult.DRAW // Scores are equal, it's a draw
            }
        }

        else -> GameResult.ONGOING // No end game condition met
    }
}


fun generateMoves(boardState: Array<Array<Piece?>>, player: Player): List<Move> {
    val moves = mutableListOf<Move>()
    val m1 = mutableListOf<Move>()
    // Iterate over the board to find pieces belonging to the current player
    for (row in boardState.indices) {

        for (col in boardState[row].indices) {

            val piece = boardState[row][col]
            if (piece?.player == player) {
                when (piece.type) {
                    PieceType.FLAGSHIP -> moves.addAll(generateFlagshipMoves(row, col, boardState))
                    PieceType.GUARD -> moves.addAll(generateGuardMoves(row, col, boardState))
                    PieceType.RAIDER -> moves.addAll(generateRaiderMoves(row, col, boardState))
                    PieceType.CANNON -> moves.addAll(
                        generateCannonMoves(
                            row,
                            col,
                            boardState,
                            piece
                        )
                    )

                    PieceType.SENTINEL -> moves.addAll(
                        generateSentinelMoves(
                            row,
                            col,
                            boardState,
                            piece
                        )
                    )
                }
            }
        }
    }

    return moves
}


fun generateFlagshipMoves(row: Int, col: Int, boardState: Array<Array<Piece?>>): List<Move> {
    val moves = mutableListOf<Move>()
    val possibleDirections = listOf(
        Pair(-1, 0), Pair(1, 0), // Vertical moves
        Pair(0, -1), Pair(0, 1), // Horizontal moves
        Pair(-1, -1), Pair(-1, 1), // Diagonal moves
        Pair(1, -1), Pair(1, 1)  // Diagonal moves
    )

    for (direction in possibleDirections) {
        val newRow = row + direction.first
        val newCol = col + direction.second
        // Check if the new position is within bounds
        if (newRow in 0 until boardSize && newCol in 0 until boardSize) {
            // Check if the new position is either empty or occupied by an opponent (which can be captured)
            if (boardState[newRow][newCol] == null || boardState[newRow][newCol]?.player != boardState[row][col]?.player) {
                moves.add(
                    Move(
                        from = Pair(row, col),
                        to = Pair(newRow, newCol),
                        action = Action.MOVE
                    )
                )
            }
        }
    }
    return moves
}


fun generateGuardMoves(row: Int, col: Int, boardState: Array<Array<Piece?>>): List<Move> {
    val moves = mutableListOf<Move>()
    // Define directions: up, down, left, right
    val directions = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))

    directions.forEach { (dRow, dCol) ->
        var currentRow = row + dRow
        var currentCol = col + dCol
        // Continue in the direction until the Guard is blocked by another piece or the edge of the board
        while (currentRow in 0 until boardSize && currentCol in 0 until boardSize) {
            // Check the square for a piece
            val currentPiece = boardState[currentRow][currentCol]
            if (currentPiece != null) {
                // If it's an opponent's piece, it can be captured, add the move and break
                if (currentPiece.player != boardState[row][col]?.player) {
                    moves.add(
                        Move(
                            from = Pair(row, col),
                            to = Pair(currentRow, currentCol),
                            action = Action.MOVE
                        )
                    )
                }
                break // Whether it's an opponent's piece or not, the guard can't move further in this direction
            } else {
                // If the square is empty, add the move
                moves.add(
                    Move(
                        from = Pair(row, col),
                        to = Pair(currentRow, currentCol),
                        action = Action.MOVE
                    )
                )
            }
            // Move to the next square in the direction
            currentRow += dRow
            currentCol += dCol
        }
    }

    return moves
}


fun generateRaiderMoves(row: Int, col: Int, boardState: Array<Array<Piece?>>): List<Move> {
    val moves = mutableListOf<Move>()
    // Define diagonal directions: top-left, top-right, bottom-left, bottom-right
    val directions = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))

    // Iterate through each diagonal direction
    for (direction in directions) {
        var currentRow = row
        var currentCol = col
        // Move in a diagonal line until blocked
        while (true) {
            currentRow += direction.first
            currentCol += direction.second

            // Break if off the board
            if (currentRow !in 0 until boardSize || currentCol !in 0 until boardSize) break

            val currentPiece = boardState[currentRow][currentCol]

            // If the current square is occupied by an opponent's piece, the Raider can capture it.
            if (currentPiece != null) {
                if (currentPiece.player != boardState[row][col]?.player) {
                    moves.add(
                        Move(
                            from = Pair(row, col),
                            to = Pair(currentRow, currentCol),
                            action = Action.MOVE
                        )
                    )
                }
                break // Blocked by a piece, so stop checking further in this direction
            } else {
                // Add the move to the list as the square is empty and reachable
                moves.add(
                    Move(
                        from = Pair(row, col),
                        to = Pair(currentRow, currentCol),
                        action = Action.MOVE
                    )
                )
            }
        }
    }

    return moves
}


fun generateCannonMoves(
    row: Int,
    col: Int,
    boardState: Array<Array<Piece?>>,
    piece: Piece
): List<Move> {

    if (piece.remainingShots <= 0) return emptyList()

    val moves = mutableListOf<Move>()

    // Cannons fire in eight directions: vertically, horizontally, and diagonally
    val directions = listOf(
        Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0), // Horizontal and vertical
        Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)  // Diagonal
    )

    for (direction in directions) {
        var range = 1 // Start with one square away
        var currentRow = row + direction.first
        var currentCol = col + direction.second

        while (range <= 4 && currentRow in 0 until boardSize && currentCol in 0 until boardSize) {
            val targetPiece = boardState[currentRow][currentCol]
            if (targetPiece != null) {
                // Fire only if the target is the first piece in the line of sight (friend or foe)
                moves.add(
                    Move(
                        from = Pair(row, col),
                        to = Pair(currentRow, currentCol),
                        action = Action.FIRE
                    )
                )
                break // Can't fire through pieces, stop checking this direction after firing
            }
            // Prepare to check the next square in this direction
            range++
            currentRow += direction.first
            currentCol += direction.second
        }
    }

    return moves
}


fun generateSentinelMoves(
    row: Int,
    col: Int,
    boardState: Array<Array<Piece?>>,
    piece: Piece
): List<Move> {
    val moves = mutableListOf<Move>()
    // Sentinels can move one square in any direction or fire up to 3 squares in a straight line or diagonal
    val moveDirections = listOf(
        Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0), // Horizontal and vertical
        Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)  // Diagonal
    )

    // Generate move options (1 square in any direction)
    for (direction in moveDirections) {
        val newRow = row + direction.first
        val newCol = col + direction.second
        // Check if the move is within bounds and the square is empty
        if (newRow in 0 until boardSize && newCol in 0 until boardSize && boardState[newRow][newCol]?.player != piece.player) {
            moves.add(Move(from = Pair(row, col), to = Pair(newRow, newCol), action = Action.MOVE))
        }
    }

    if (piece.remainingShots <= 0) return moves

    // Generate firing options (up to 3 squares in any direction)
    for (direction in moveDirections) {
        var range = 1 // Start with one square away
        var currentRow = row + direction.first
        var currentCol = col + direction.second

        while (range <= 3 && currentRow in 0 until boardSize && currentCol in 0 until boardSize) {
            val targetPiece = boardState[currentRow][currentCol]
            if (targetPiece != null) {
                // Sentinels can fire if the target is the first piece in line of sight
                moves.add(
                    Move(
                        from = Pair(row, col),
                        to = Pair(currentRow, currentCol),
                        action = Action.FIRE
                    )
                )
                break // Stop checking further in this direction after an encounter
            }
            // Prepare to check the next square in this direction
            range++
            currentRow += direction.first
            currentCol += direction.second
        }
    }

    return moves
}


// Define a Move data class to represent the moves
data class Move(val from: Pair<Int, Int>, val to: Pair<Int, Int>, val action: Action)

