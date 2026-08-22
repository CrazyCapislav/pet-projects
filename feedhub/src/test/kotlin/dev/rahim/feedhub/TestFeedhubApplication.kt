package dev.rahim.feedhub

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<FeedhubApplication>().with(TestcontainersConfiguration::class).run(*args)
}
