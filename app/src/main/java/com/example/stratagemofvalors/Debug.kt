package com.example.stratagemofvalors

import android.util.Log

fun printBoard(boardState: Array<Array<Piece?>>)
{
    for (row in boardState.indices) {
        var s = ""
        for (col in boardState[row].indices) {
            val piece = boardState[row][col]

            if (piece == null)
            {
                s = s + ".."
            }
            else {
                if (piece.player == Player.ONE) s = s+"B" else s = s+"R"
                when (piece.type) {
                    PieceType.FLAGSHIP -> s = s + "F"
                    PieceType.SENTINEL -> s = s + "S"
                    else -> s = s+".."
                }
            }
        }

        Log.d("OUT", "$s")
    }
}


