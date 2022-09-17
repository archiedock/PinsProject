package com.example.pins.usecase

import com.example.pins.interfaces.Person

class Greeting(val delegate: Person) {

    fun sayHello() = delegate.sayHello()
}