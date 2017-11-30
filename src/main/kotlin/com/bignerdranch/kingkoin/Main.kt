package com.bignerdranch.kingkoin

import kotlin.concurrent.fixedRateTimer

fun main(args: Array<String>) {

    fixedRateTimer("kingkoin advice", period = 5000L) {
        println(KingKoin.investmentAdvice())
        println(".....")
    }

}