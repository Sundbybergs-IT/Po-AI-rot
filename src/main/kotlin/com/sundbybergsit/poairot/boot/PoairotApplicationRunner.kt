package com.sundbybergsit.poairot.boot

import com.sundbybergsit.poairot.PoairotApplication
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class PoairotApplicationRunner(private val poairotApplication: PoairotApplication) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (args.sourceArgs.isNotEmpty()) {
            poairotApplication.interrogate(args.sourceArgs[0])
        }
    }
}
